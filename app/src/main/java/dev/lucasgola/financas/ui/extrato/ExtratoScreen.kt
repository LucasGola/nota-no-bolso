package dev.lucasgola.financas.ui.extrato

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.IconButton
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.lucasgola.financas.data.AppDatabase
import dev.lucasgola.financas.data.LancamentoComCategoria
import dev.lucasgola.financas.data.Categoria
import dev.lucasgola.financas.data.Estabelecimento
import dev.lucasgola.financas.filtro.ConsultaLancamentos
import dev.lucasgola.financas.filtro.Filtro
import dev.lucasgola.financas.export.Exportador
import dev.lucasgola.financas.ui.exportar.DialogoExportar
import dev.lucasgola.financas.ui.filtro.BarraFiltros
import dev.lucasgola.financas.util.ZONA
import dev.lucasgola.financas.data.TipoLancamento
import dev.lucasgola.financas.R
import dev.lucasgola.financas.ui.importar.DialogoDigitarChave
import dev.lucasgola.financas.ui.theme.CoresValor
import dev.lucasgola.financas.util.dataLocal
import dev.lucasgola.financas.util.formatarDiaExtrato
import dev.lucasgola.financas.util.formatarMoeda
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate

data class DiaExtrato(val data: LocalDate, val saldoCentavos: Long, val itens: List<LancamentoComCategoria>)

data class ExtratoUi(
    val entradasCentavos: Long = 0,
    val saidasCentavos: Long = 0,
    val dias: List<DiaExtrato> = emptyList(),
    val carregando: Boolean = true,
) {
    val saldoCentavos: Long get() = entradasCentavos - saidasCentavos
}

private fun LancamentoComCategoria.valorComSinal(): Long =
    if (lancamento.tipo == TipoLancamento.ENTRADA) lancamento.valorCentavos else -lancamento.valorCentavos

@OptIn(ExperimentalCoroutinesApi::class)
class ExtratoViewModel(db: AppDatabase, private val filtroGlobal: MutableStateFlow<Filtro>) : ViewModel() {
    val pendentes: StateFlow<Int> = db.notaDao().contarPendentes()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    val filtro: StateFlow<Filtro> = filtroGlobal.asStateFlow()

    val categorias: StateFlow<List<Categoria>> = db.categoriaDao().observarTodas()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val estabelecimentos: StateFlow<List<Estabelecimento>> = db.estabelecimentoDao().observarTodos()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun alterarFiltro(f: (Filtro) -> Filtro) = filtroGlobal.update(f)

    val ui: StateFlow<ExtratoUi> = filtroGlobal
        .flatMapLatest { f -> db.lancamentoDao().observarFiltrado(ConsultaLancamentos.montar(f, LocalDate.now(ZONA)).paraRoom()) }
        .map { lista ->
            ExtratoUi(
                entradasCentavos = lista.filter { it.lancamento.tipo == TipoLancamento.ENTRADA }.sumOf { it.lancamento.valorCentavos },
                saidasCentavos = lista.filter { it.lancamento.tipo == TipoLancamento.SAIDA }.sumOf { it.lancamento.valorCentavos },
                dias = lista.groupBy { it.lancamento.dataHora.dataLocal() }
                    .map { (dia, itens) -> DiaExtrato(dia, itens.sumOf { it.valorComSinal() }, itens) },
                carregando = false,
            )
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ExtratoUi())
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExtratoScreen(
    db: AppDatabase,
    filtro: MutableStateFlow<Filtro>,
    exportador: Exportador,
    onNovo: () -> Unit,
    onAbrir: (Long) -> Unit,
    onLerQr: () -> Unit,
    onImportarUrl: (String) -> Unit,
    onVerPendentes: () -> Unit,
    onBackup: () -> Unit,
) {
    val vm: ExtratoViewModel = viewModel { ExtratoViewModel(db, filtro) }
    val ui by vm.ui.collectAsStateWithLifecycle()
    val pendentes by vm.pendentes.collectAsStateWithLifecycle()
    val filtroAtual by vm.filtro.collectAsStateWithLifecycle()
    val categorias by vm.categorias.collectAsStateWithLifecycle()
    val estabelecimentos by vm.estabelecimentos.collectAsStateWithLifecycle()
    var menuAberto by remember { mutableStateOf(false) }
    var digitandoChave by remember { mutableStateOf(false) }
    var exportando by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Extrato") },
                actions = {
                    IconButton(onClick = { menuAberto = true }) { Icon(Icons.Default.MoreVert, "Mais opções") }
                    DropdownMenu(expanded = menuAberto, onDismissRequest = { menuAberto = false }) {
                        DropdownMenuItem(
                            text = { Text("Exportar (CSV ou PDF)") },
                            onClick = { menuAberto = false; exportando = true },
                        )
                        DropdownMenuItem(
                            text = { Text("Digitar chave de acesso") },
                            onClick = { menuAberto = false; digitandoChave = true },
                        )
                        DropdownMenuItem(
                            text = { Text("Notas pendentes ($pendentes)") },
                            onClick = { menuAberto = false; onVerPendentes() },
                        )
                        DropdownMenuItem(
                            text = { Text("Backup e restauração") },
                            onClick = { menuAberto = false; onBackup() },
                        )
                    }
                },
            )
        },
        floatingActionButton = {
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                SmallFloatingActionButton(onClick = onNovo) {
                    Icon(Icons.Default.Add, contentDescription = "Novo lançamento manual")
                }
                ExtendedFloatingActionButton(
                    onClick = onLerQr,
                    icon = { Icon(painterResource(R.drawable.ic_qr), contentDescription = null) },
                    text = { Text("Ler nota") },
                )
            }
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            BarraFiltros(
                filtro = filtroAtual,
                categorias = categorias,
                estabelecimentos = estabelecimentos,
                onAlterar = vm::alterarFiltro,
                modifier = Modifier.padding(bottom = 4.dp),
            )
            LazyColumn(Modifier.fillMaxWidth().weight(1f)) {
                if (pendentes > 0) item {
                    Card(
                        onClick = onVerPendentes,
                        modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
                    ) {
                        Text(
                            if (pendentes == 1) "1 nota pendente de importação. Toque para ver."
                            else "$pendentes notas pendentes de importação. Toque para ver.",
                            Modifier.padding(16.dp),
                            color = MaterialTheme.colorScheme.onTertiaryContainer,
                        )
                    }
                }
                item { Resumo(ui) }
                if (!ui.carregando && ui.dias.isEmpty()) {
                    item {
                        Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                            Text(
                                if (filtroAtual.temRestricoes) "Nenhum lançamento com estes filtros."
                                else "Nenhum lançamento neste período.\nLeia o QR de uma nota ou toque em + para lançar à mão.",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                            )
                        }
                    }
                }
                ui.dias.forEach { dia ->
                    item(key = "dia-${dia.data}") { CabecalhoDia(dia) }
                    items(dia.itens, key = { it.lancamento.id }) { item ->
                        LinhaLancamento(item, onClick = { onAbrir(item.lancamento.id) })
                        HorizontalDivider()
                    }
                }
                // Espaço para os botões flutuantes não cobrirem o último lançamento.
                item { Spacer(Modifier.height(140.dp)) }
            }
        }
    }

    if (exportando) {
        DialogoExportar(
            exportador = exportador,
            filtro = filtroAtual,
            descricaoPeriodo = filtroAtual.periodo.rotulo(LocalDate.now(ZONA)) +
                if (filtroAtual.temRestricoes) " (com filtros)" else "",
            onFechar = { exportando = false },
        )
    }

    if (digitandoChave) {
        DialogoDigitarChave(
            onConfirmar = { url -> digitandoChave = false; onImportarUrl(url) },
            onCancelar = { digitandoChave = false },
        )
    }
}

@Composable
private fun Resumo(ui: ExtratoUi) {
    Card(Modifier.fillMaxWidth().padding(16.dp)) {
        Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            ValorResumo("Entradas", ui.entradasCentavos, CoresValor.entrada)
            ValorResumo("Saídas", ui.saidasCentavos, CoresValor.saida)
            ValorResumo("Saldo", ui.saldoCentavos, if (ui.saldoCentavos >= 0) CoresValor.entrada else CoresValor.saida)
        }
    }
}

@Composable
private fun ValorResumo(rotulo: String, centavos: Long, cor: Color) {
    Column {
        Text(rotulo, style = MaterialTheme.typography.labelMedium)
        Text(formatarMoeda(centavos), style = MaterialTheme.typography.titleMedium, color = cor, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun CabecalhoDia(dia: DiaExtrato) {
    Row(
        Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant).padding(horizontal = 16.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(dia.data.formatarDiaExtrato(), style = MaterialTheme.typography.labelLarge)
        Text(formatarMoeda(dia.saldoCentavos), style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
private fun LinhaLancamento(item: LancamentoComCategoria, onClick: () -> Unit) {
    val l = item.lancamento
    val entrada = l.tipo == TipoLancamento.ENTRADA
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(12.dp).background(Color(item.categoriaCor), CircleShape))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                l.descricao.ifBlank { item.categoriaNome },
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(item.categoriaNome, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text(
            (if (entrada) "+ " else "− ") + formatarMoeda(l.valorCentavos),
            color = if (entrada) CoresValor.entrada else CoresValor.saida,
            style = MaterialTheme.typography.bodyLarge,
        )
    }
}
