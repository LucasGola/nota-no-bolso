package dev.lucasgola.financas.ui.categorias

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
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
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.lucasgola.financas.data.AppDatabase
import dev.lucasgola.financas.data.Categoria
import dev.lucasgola.financas.data.CategoriaDao
import dev.lucasgola.financas.data.TipoCategoria
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

private val PALETA = listOf(
    0xFF2E7D32, 0xFF00897B, 0xFF1565C0, 0xFF3949AB, 0xFF8E24AA, 0xFFC2185B,
    0xFFC62828, 0xFFEF6C00, 0xFFF9A825, 0xFF6D4C41, 0xFF546E7A, 0xFF757575,
)

class CategoriasViewModel(private val dao: CategoriaDao) : ViewModel() {
    val categorias: StateFlow<List<Categoria>> = dao.observarTodas()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val mensagem = MutableStateFlow<String?>(null)

    fun salvar(c: Categoria) = viewModelScope.launch {
        if (c.id == 0L) dao.inserir(c) else dao.atualizar(c)
    }

    /** Categoria com lançamentos não é apagada: perderia o histórico. Só pode ser desativada. */
    fun excluir(c: Categoria) = viewModelScope.launch {
        if (dao.contarLancamentos(c.id) > 0) {
            mensagem.value = "\"${c.nome}\" tem lançamentos. Desative em vez de excluir."
        } else {
            dao.excluir(c)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoriasScreen(db: AppDatabase) {
    val vm: CategoriasViewModel = viewModel { CategoriasViewModel(db.categoriaDao()) }
    val categorias by vm.categorias.collectAsStateWithLifecycle()
    val mensagem by vm.mensagem.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var editando by remember { mutableStateOf<Categoria?>(null) }

    LaunchedEffect(mensagem) {
        mensagem?.let {
            snackbar.showSnackbar(it)
            vm.mensagem.value = null
        }
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Categorias") }) },
        snackbarHost = { SnackbarHost(snackbar) },
        floatingActionButton = {
            FloatingActionButton(onClick = {
                editando = Categoria(nome = "", cor = PALETA.first(), tipo = TipoCategoria.SAIDA)
            }) { Icon(Icons.Default.Add, "Nova categoria") }
        },
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding)) {
            items(categorias, key = { it.id }) { c ->
                Row(
                    Modifier.fillMaxWidth().clickable { editando = c }.padding(16.dp).alpha(if (c.ativa) 1f else 0.5f),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.size(16.dp).background(Color(c.cor), CircleShape))
                    Spacer(Modifier.width(16.dp))
                    Column(Modifier.weight(1f)) {
                        Text(c.nome, style = MaterialTheme.typography.bodyLarge)
                        Text(
                            c.tipo.rotulo() + if (c.ativa) "" else " · inativa",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                HorizontalDivider()
            }
        }
    }

    editando?.let { c ->
        DialogoCategoria(
            inicial = c,
            onSalvar = { vm.salvar(it); editando = null },
            onExcluir = if (c.id != 0L) ({ vm.excluir(c); editando = null }) else null,
            onCancelar = { editando = null },
        )
    }
}

private fun TipoCategoria.rotulo() = when (this) {
    TipoCategoria.ENTRADA -> "Entrada"
    TipoCategoria.SAIDA -> "Saída"
    TipoCategoria.AMBOS -> "Entrada e saída"
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DialogoCategoria(
    inicial: Categoria,
    onSalvar: (Categoria) -> Unit,
    onExcluir: (() -> Unit)?,
    onCancelar: () -> Unit,
) {
    var c by remember(inicial) { mutableStateOf(inicial) }
    AlertDialog(
        onDismissRequest = onCancelar,
        title = { Text(if (inicial.id == 0L) "Nova categoria" else "Editar categoria") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(c.nome, { c = c.copy(nome = it) }, label = { Text("Nome") }, singleLine = true)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TipoCategoria.entries.forEach { t ->
                        FilterChip(selected = c.tipo == t, onClick = { c = c.copy(tipo = t) }, label = { Text(t.rotulo()) })
                    }
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    PALETA.forEach { cor ->
                        Box(
                            Modifier.size(32.dp).background(Color(cor), CircleShape)
                                .then(
                                    if (c.cor == cor) Modifier.border(3.dp, MaterialTheme.colorScheme.onSurface, CircleShape)
                                    else Modifier
                                )
                                .clickable { c = c.copy(cor = cor) },
                        )
                    }
                }
                if (inicial.id != 0L) Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Ativa", Modifier.weight(1f))
                    Switch(checked = c.ativa, onCheckedChange = { c = c.copy(ativa = it) })
                }
            }
        },
        confirmButton = {
            TextButton(enabled = c.nome.isNotBlank(), onClick = { onSalvar(c.copy(nome = c.nome.trim())) }) { Text("Salvar") }
        },
        dismissButton = {
            Row {
                if (onExcluir != null) TextButton(onClick = onExcluir) { Text("Excluir") }
                TextButton(onClick = onCancelar) { Text("Cancelar") }
            }
        },
    )
}
