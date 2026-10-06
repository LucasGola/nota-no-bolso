package dev.lucasgola.financas.ui.importar

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.lucasgola.financas.data.AppDatabase
import dev.lucasgola.financas.data.Categoria
import dev.lucasgola.financas.data.NotaFiscal
import dev.lucasgola.financas.data.TipoLancamento
import dev.lucasgola.financas.nfce.ChaveAcesso
import dev.lucasgola.financas.nfce.ImportacaoRepository
import dev.lucasgola.financas.ui.comum.CampoData
import dev.lucasgola.financas.ui.comum.CampoValor
import dev.lucasgola.financas.ui.comum.SeletorCategoria
import dev.lucasgola.financas.util.ZONA
import dev.lucasgola.financas.util.formatarCnpj
import dev.lucasgola.financas.util.meioDia
import dev.lucasgola.financas.util.parseCentavosBr
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter

class PendentesViewModel(private val repo: ImportacaoRepository, db: AppDatabase) : ViewModel() {
    val pendentes: StateFlow<List<NotaFiscal>> = db.notaDao().observarPendentes()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val categorias: StateFlow<List<Categoria>> = db.categoriaDao().observarTodas()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val processando = MutableStateFlow(false)
    val mensagem = MutableStateFlow<String?>(null)

    fun reprocessarTodas() {
        if (processando.value) return
        processando.value = true
        viewModelScope.launch {
            val r = repo.reprocessarPendentes(pendentes.value)
            processando.value = false
            mensagem.value = when {
                r.falhas == 0 -> "${r.importadas} nota(s) importada(s). Revise a categoria no extrato."
                r.importadas == 0 -> "Nenhuma nota pôde ser importada ainda."
                else -> "${r.importadas} importada(s), ${r.falhas} ainda pendente(s)."
            }
        }
    }

    fun completarManual(nota: NotaFiscal, valorCentavos: Long, categoriaId: Long, descricao: String, data: LocalDate) =
        viewModelScope.launch {
            repo.completarManual(nota, valorCentavos, categoriaId, descricao, data.meioDia())
            mensagem.value = "Lançamento criado."
        }

    fun descartar(nota: NotaFiscal) = viewModelScope.launch { repo.descartarPendente(nota) }
}

private val fmtMes = DateTimeFormatter.ofPattern("MM/yyyy")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PendentesScreen(
    db: AppDatabase,
    repo: ImportacaoRepository,
    onTentarNovamente: (urlQr: String) -> Unit,
    onFechar: () -> Unit,
) {
    val vm: PendentesViewModel = viewModel { PendentesViewModel(repo, db) }
    val pendentes by vm.pendentes.collectAsStateWithLifecycle()
    val categorias by vm.categorias.collectAsStateWithLifecycle()
    val processando by vm.processando.collectAsStateWithLifecycle()
    val mensagem by vm.mensagem.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var completando by remember { mutableStateOf<NotaFiscal?>(null) }
    var descartando by remember { mutableStateOf<NotaFiscal?>(null) }

    LaunchedEffect(mensagem) {
        mensagem?.let { snackbar.showSnackbar(it); vm.mensagem.value = null }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Notas pendentes") },
                navigationIcon = { IconButton(onClick = onFechar) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Voltar") } },
                actions = {
                    if (pendentes.isNotEmpty()) IconButton(onClick = vm::reprocessarTodas, enabled = !processando) {
                        Icon(Icons.Default.Refresh, "Reprocessar todas")
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            if (processando) LinearProgressIndicator(Modifier.fillMaxWidth())
            if (pendentes.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Nenhuma nota pendente.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(pendentes, key = { it.id }) { nota ->
                    val chave = ChaveAcesso.of(nota.chaveAcesso)
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("NFC-e nº ${nota.numero} · ${chave?.anoMes?.format(fmtMes).orEmpty()}", style = MaterialTheme.typography.titleSmall)
                            Text(
                                "CNPJ ${formatarCnpj(chave?.cnpjEmitente.orEmpty())}",
                                style = MaterialTheme.typography.bodySmall,
                            )
                            nota.erroMsg?.let {
                                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                TextButton(onClick = { onTentarNovamente(nota.urlQr) }) { Text("Tentar de novo") }
                                TextButton(onClick = { completando = nota }) { Text("Informar valor") }
                                TextButton(onClick = { descartando = nota }) { Text("Descartar") }
                            }
                        }
                    }
                }
            }
        }
    }

    completando?.let { nota ->
        DialogoCompletarManual(
            categorias = categorias.filter { it.ativa && it.tipo.aceita(TipoLancamento.SAIDA) },
            numeroNota = nota.numero,
            onConfirmar = { valor, categoriaId, descricao, data ->
                vm.completarManual(nota, valor, categoriaId, descricao, data)
                completando = null
            },
            onCancelar = { completando = null },
        )
    }

    descartando?.let { nota ->
        AlertDialog(
            onDismissRequest = { descartando = null },
            title = { Text("Descartar nota pendente?") },
            text = { Text("Ela some da lista e não gera lançamento. Dá para ler o QR de novo depois.") },
            confirmButton = { TextButton(onClick = { vm.descartar(nota); descartando = null }) { Text("Descartar") } },
            dismissButton = { TextButton(onClick = { descartando = null }) { Text("Cancelar") } },
        )
    }
}

@Composable
private fun DialogoCompletarManual(
    categorias: List<Categoria>,
    numeroNota: Long,
    onConfirmar: (valorCentavos: Long, categoriaId: Long, descricao: String, data: LocalDate) -> Unit,
    onCancelar: () -> Unit,
) {
    var valor by remember { mutableStateOf("") }
    var categoriaId by remember { mutableStateOf<Long?>(null) }
    var descricao by remember { mutableStateOf("NFC-e nº $numeroNota") }
    var data by remember { mutableStateOf(LocalDate.now(ZONA)) }
    val centavos = parseCentavosBr(valor)
    val valido = centavos != null && centavos > 0 && categoriaId != null

    AlertDialog(
        onDismissRequest = onCancelar,
        title = { Text("Informar valor da nota") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                CampoValor(valor, { valor = it })
                SeletorCategoria(categorias, categoriaId, { categoriaId = it })
                OutlinedTextField(descricao, { descricao = it }, label = { Text("Descrição") }, singleLine = true)
                CampoData(data, { data = it })
            }
        },
        confirmButton = {
            TextButton(enabled = valido, onClick = { onConfirmar(centavos!!, categoriaId!!, descricao.trim(), data) }) {
                Text("Salvar")
            }
        },
        dismissButton = { TextButton(onClick = onCancelar) { Text("Cancelar") } },
    )
}
