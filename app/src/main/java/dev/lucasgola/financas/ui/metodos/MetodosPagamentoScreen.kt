package dev.lucasgola.financas.ui.metodos

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.lucasgola.financas.data.MetodoPagamento
import dev.lucasgola.financas.data.MetodoPagamentoDao
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class MetodosPagamentoViewModel(private val dao: MetodoPagamentoDao) : ViewModel() {
    val metodos: StateFlow<List<MetodoPagamento>> = dao.observarTodos()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val mensagem = MutableStateFlow<String?>(null)

    fun salvar(m: MetodoPagamento) = viewModelScope.launch {
        if (m.id == 0L) dao.inserir(m) else dao.atualizar(m)
    }

    /** Como nas categorias: método já usado não é apagado (sumiria do histórico), só desativado. */
    fun excluir(m: MetodoPagamento) = viewModelScope.launch {
        if (dao.contarLancamentos(m.id) > 0) {
            mensagem.value = "\"${m.nome}\" já foi usado em lançamentos. Desative em vez de excluir."
        } else {
            dao.excluir(m)
        }
    }
}

/** Lista e edição dos métodos de pagamento. [topo] é a barra superior, compartilhada com as categorias. */
@Composable
fun MetodosPagamentoScreen(dao: MetodoPagamentoDao, topo: @Composable () -> Unit) {
    val vm: MetodosPagamentoViewModel = viewModel { MetodosPagamentoViewModel(dao) }
    val metodos by vm.metodos.collectAsStateWithLifecycle()
    val mensagem by vm.mensagem.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var editando by remember { mutableStateOf<MetodoPagamento?>(null) }

    LaunchedEffect(mensagem) {
        mensagem?.let {
            snackbar.showSnackbar(it)
            vm.mensagem.value = null
        }
    }

    Scaffold(
        topBar = topo,
        snackbarHost = { SnackbarHost(snackbar) },
        floatingActionButton = {
            FloatingActionButton(onClick = { editando = MetodoPagamento(nome = "") }) {
                Icon(Icons.Default.Add, "Novo método de pagamento")
            }
        },
    ) { padding ->
        if (metodos.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding).padding(32.dp), contentAlignment = Alignment.Center) {
                Text(
                    "Cadastre de onde sai o dinheiro: VR, VA, o cartão de cada banco…\n\n" +
                        "Serve para separar o que a nota registra só como \"Débito\" ou \"Crédito\". " +
                        "O app guarda apenas o nome e uma descrição, nenhum dado do cartão.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        }
        LazyColumn(Modifier.fillMaxSize().padding(padding)) {
            items(metodos, key = { it.id }) { m ->
                Column(Modifier.fillMaxWidth().clickable { editando = m }.padding(16.dp).alpha(if (m.ativo) 1f else 0.5f)) {
                    Text(m.nome, style = MaterialTheme.typography.bodyLarge)
                    val detalhe = listOfNotNull(m.descricao?.takeIf { it.isNotBlank() }, "inativo".takeIf { !m.ativo })
                    if (detalhe.isNotEmpty()) Text(
                        detalhe.joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                HorizontalDivider()
            }
        }
    }

    editando?.let { m ->
        DialogoMetodo(
            inicial = m,
            onSalvar = { vm.salvar(it); editando = null },
            onExcluir = if (m.id != 0L) ({ vm.excluir(m); editando = null }) else null,
            onCancelar = { editando = null },
        )
    }
}

@Composable
private fun DialogoMetodo(
    inicial: MetodoPagamento,
    onSalvar: (MetodoPagamento) -> Unit,
    onExcluir: (() -> Unit)?,
    onCancelar: () -> Unit,
) {
    var m by remember(inicial) { mutableStateOf(inicial) }
    AlertDialog(
        onDismissRequest = onCancelar,
        title = { Text(if (inicial.id == 0L) "Novo método de pagamento" else "Editar método de pagamento") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    m.nome, { m = m.copy(nome = it) },
                    label = { Text("Nome") }, placeholder = { Text("Ex.: VR") }, singleLine = true,
                )
                OutlinedTextField(
                    m.descricao.orEmpty(), { m = m.copy(descricao = it) },
                    label = { Text("Descrição (opcional)") }, placeholder = { Text("Ex.: vale-refeição da empresa") },
                )
                if (inicial.id != 0L) Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Ativo", Modifier.weight(1f))
                    Switch(checked = m.ativo, onCheckedChange = { m = m.copy(ativo = it) })
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = m.nome.isNotBlank(),
                onClick = { onSalvar(m.copy(nome = m.nome.trim(), descricao = m.descricao?.trim()?.ifEmpty { null })) },
            ) { Text("Salvar") }
        },
        dismissButton = {
            Row {
                if (onExcluir != null) TextButton(onClick = onExcluir) { Text("Excluir") }
                TextButton(onClick = onCancelar) { Text("Cancelar") }
            }
        },
    )
}
