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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.lucasgola.financas.data.AppDatabase
import dev.lucasgola.financas.data.LancamentoComCategoria
import dev.lucasgola.financas.data.LancamentoDao
import dev.lucasgola.financas.data.TipoLancamento
import dev.lucasgola.financas.ui.theme.CoresValor
import dev.lucasgola.financas.util.dataLocal
import dev.lucasgola.financas.util.formatarDiaExtrato
import dev.lucasgola.financas.util.formatarMoeda
import kotlinx.coroutines.flow.SharingStarted
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

class ExtratoViewModel(dao: LancamentoDao) : ViewModel() {
    // Filtros chegam no M3; por ora o extrato mostra tudo.
    val ui: StateFlow<ExtratoUi> = dao.observarExtrato()
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
fun ExtratoScreen(db: AppDatabase, onNovo: () -> Unit, onAbrir: (Long) -> Unit) {
    val vm: ExtratoViewModel = viewModel { ExtratoViewModel(db.lancamentoDao()) }
    val ui by vm.ui.collectAsStateWithLifecycle()

    Scaffold(
        topBar = { TopAppBar(title = { Text("Extrato") }) },
        floatingActionButton = {
            FloatingActionButton(onClick = onNovo) { Icon(Icons.Default.Add, contentDescription = "Novo lançamento") }
        },
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding)) {
            item { Resumo(ui) }
            if (!ui.carregando && ui.dias.isEmpty()) {
                item {
                    Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                        Text(
                            "Nenhum lançamento ainda.\nToque em + para adicionar.",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
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
        }
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
