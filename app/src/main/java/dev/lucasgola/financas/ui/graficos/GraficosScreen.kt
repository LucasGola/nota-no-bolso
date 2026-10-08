package dev.lucasgola.financas.ui.graficos

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.lucasgola.financas.data.AppDatabase
import dev.lucasgola.financas.data.Categoria
import dev.lucasgola.financas.data.Estabelecimento
import dev.lucasgola.financas.data.TipoLancamento
import dev.lucasgola.financas.filtro.ConsultaLancamentos
import dev.lucasgola.financas.filtro.Filtro
import dev.lucasgola.financas.filtro.Periodo
import dev.lucasgola.financas.graficos.FatiaCategoria
import dev.lucasgola.financas.graficos.FatiaEstabelecimento
import dev.lucasgola.financas.graficos.ResumoMes
import dev.lucasgola.financas.graficos.evolucaoMensal
import dev.lucasgola.financas.graficos.mesesDaEvolucao
import dev.lucasgola.financas.graficos.porCategoria
import dev.lucasgola.financas.graficos.topEstabelecimentos
import dev.lucasgola.financas.ui.filtro.BarraFiltros
import dev.lucasgola.financas.ui.theme.CoresGrafico
import dev.lucasgola.financas.ui.theme.coresGrafico
import dev.lucasgola.financas.util.LOCALE_BR
import dev.lucasgola.financas.util.ZONA
import dev.lucasgola.financas.util.formatarMoeda
import dev.lucasgola.financas.util.formatarMoedaCompacta
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import kotlin.math.ceil
import kotlin.math.log10
import kotlin.math.pow

data class GraficosUi(
    val tipoCategorias: TipoLancamento = TipoLancamento.SAIDA,
    val totalCentavos: Long = 0,
    val categorias: List<FatiaCategoria> = emptyList(),
    val estabelecimentos: List<FatiaEstabelecimento> = emptyList(),
    val evolucao: List<ResumoMes> = emptyList(),
    val carregando: Boolean = true,
)

@OptIn(ExperimentalCoroutinesApi::class)
class GraficosViewModel(db: AppDatabase, private val filtroGlobal: MutableStateFlow<Filtro>) : ViewModel() {
    private val dao = db.lancamentoDao()
    private fun hoje() = LocalDate.now(ZONA)

    val filtro: StateFlow<Filtro> = filtroGlobal.asStateFlow()
    val categorias: StateFlow<List<Categoria>> = db.categoriaDao().observarTodas()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val estabelecimentos: StateFlow<List<Estabelecimento>> = db.estabelecimentoDao().observarTodos()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun alterarFiltro(f: (Filtro) -> Filtro) = filtroGlobal.update(f)

    private val doPeriodo = filtroGlobal.flatMapLatest { f ->
        dao.observarFiltrado(ConsultaLancamentos.montar(f, hoje()).paraRoom())
    }

    /** Mesmas restrições do filtro, mas numa janela de meses própria (ver [mesesDaEvolucao]). */
    private val evolucao = filtroGlobal.flatMapLatest { f ->
        val meses = mesesDaEvolucao(f.periodo, hoje())
        val janela = f.copy(periodo = Periodo.Personalizado(meses.first().atDay(1), meses.last().atEndOfMonth()))
        dao.observarFiltrado(ConsultaLancamentos.montar(janela, hoje()).paraRoom()).map { evolucaoMensal(it, meses) }
    }

    val ui: StateFlow<GraficosUi> = combine(doPeriodo, evolucao, estabelecimentos, filtroGlobal) { lista, evo, ests, f ->
        // Filtrando só entradas, o gráfico por categoria passa a mostrar entradas.
        val tipo = if (f.tipo == TipoLancamento.ENTRADA) TipoLancamento.ENTRADA else TipoLancamento.SAIDA
        GraficosUi(
            tipoCategorias = tipo,
            totalCentavos = lista.filter { it.lancamento.tipo == tipo }.sumOf { it.lancamento.valorCentavos },
            categorias = porCategoria(lista, tipo),
            estabelecimentos = topEstabelecimentos(lista, ests.associate { it.id to it.razaoSocial }),
            evolucao = evo,
            carregando = false,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), GraficosUi())
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GraficosScreen(db: AppDatabase, filtro: MutableStateFlow<Filtro>, onVerNoExtrato: () -> Unit) {
    val vm: GraficosViewModel = viewModel { GraficosViewModel(db, filtro) }
    val ui by vm.ui.collectAsStateWithLifecycle()
    val filtroAtual by vm.filtro.collectAsStateWithLifecycle()
    val categorias by vm.categorias.collectAsStateWithLifecycle()
    val estabelecimentos by vm.estabelecimentos.collectAsStateWithLifecycle()
    val cores = coresGrafico()

    // Toque num elemento = aplica o filtro correspondente e abre o extrato.
    fun detalhar(f: (Filtro) -> Filtro) {
        vm.alterarFiltro(f)
        onVerNoExtrato()
    }

    Scaffold(topBar = { TopAppBar(title = { Text("Gráficos") }) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            BarraFiltros(filtroAtual, categorias, estabelecimentos, vm::alterarFiltro, Modifier.padding(bottom = 4.dp))
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                val rotuloTipo = if (ui.tipoCategorias == TipoLancamento.ENTRADA) "Recebido" else "Gasto"
                Column {
                    Text("$rotuloTipo no período", style = MaterialTheme.typography.labelLarge)
                    Text(formatarMoeda(ui.totalCentavos), style = MaterialTheme.typography.displaySmall)
                }

                Painel(if (ui.tipoCategorias == TipoLancamento.ENTRADA) "Entradas por categoria" else "Gastos por categoria") {
                    if (ui.categorias.isEmpty()) Vazio(ui.carregando)
                    else ui.categorias.forEach { c ->
                        BarraHorizontal(
                            rotulo = c.nome,
                            valor = "${formatarMoeda(c.totalCentavos)} · ${percentual(c.fracao)}",
                            proporcao = c.totalCentavos.toFloat() / ui.categorias.first().totalCentavos,
                            cor = Color(c.cor),
                            onClick = { detalhar { it.copy(categorias = setOf(c.categoriaId)) } },
                        )
                    }
                }

                Painel("Entradas × saídas por mês") {
                    GraficoEvolucao(
                        meses = ui.evolucao,
                        cores = cores,
                        onVerMes = { m -> detalhar { it.copy(periodo = Periodo.Mes(m)) } },
                    )
                }

                Painel("Onde mais gastei") {
                    if (ui.estabelecimentos.isEmpty()) {
                        Text(
                            "Sem compras de notas fiscais neste filtro.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else ui.estabelecimentos.forEach { e ->
                        BarraHorizontal(
                            rotulo = e.nome,
                            valor = formatarMoeda(e.totalCentavos),
                            proporcao = e.totalCentavos.toFloat() / ui.estabelecimentos.first().totalCentavos,
                            cor = cores.serie,
                            onClick = { detalhar { it.copy(estabelecimentos = setOf(e.estabelecimentoId)) } },
                        )
                    }
                }
                Text(
                    "Toque numa barra para ver os lançamentos no extrato.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

private fun percentual(fracao: Double): String =
    java.text.NumberFormat.getPercentInstance(LOCALE_BR).apply { maximumFractionDigits = 0 }.format(fracao)

@Composable
private fun Painel(titulo: String, conteudo: @Composable () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(titulo, style = MaterialTheme.typography.titleMedium)
            conteudo()
        }
    }
}

@Composable
private fun Vazio(carregando: Boolean) {
    Text(
        if (carregando) "Carregando…" else "Nada neste filtro.",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/**
 * Barra horizontal com rótulo e valor em texto (a cor nunca é a única identificação).
 * Extremidade arredondada de 4dp só na ponta do dado; a base fica reta, ancorada no zero.
 */
@Composable
private fun BarraHorizontal(rotulo: String, valor: String, proporcao: Float, cor: Color, onClick: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 4.dp)
            .semantics(mergeDescendants = true) {},
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(rotulo, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.width(8.dp))
            Text(valor, style = MaterialTheme.typography.bodyMedium)
        }
        Box(
            Modifier.fillMaxWidth(proporcao.coerceIn(0.01f, 1f)).height(10.dp)
                .background(cor, RoundedCornerShape(topEnd = 4.dp, bottomEnd = 4.dp)),
        )
    }
}

private val fmtMesCurto = DateTimeFormatter.ofPattern("MMM", LOCALE_BR)
private val fmtMesLongo = DateTimeFormatter.ofPattern("MMMM yyyy", LOCALE_BR)

/**
 * Arredonda o topo da escala para um valor "redondo" logo acima do máximo
 * (ex.: R$ 6,5 mil → R$ 8 mil). Passos só de 1/2/5 desperdiçavam até metade da altura.
 */
internal fun topoDaEscala(maximo: Long): Long {
    if (maximo <= 0) return 100_00 // R$ 100 quando não há dados
    val ordem = 10.0.pow(ceil(log10(maximo.toDouble())) - 1)
    val passo = listOf(1.0, 1.5, 2.0, 2.5, 3.0, 4.0, 5.0, 6.0, 8.0, 10.0).first { it * ordem >= maximo }
    return (passo * ordem).toLong()
}

/**
 * Barras agrupadas (entradas e saídas) por mês, num eixo só. Tocar num mês seleciona e mostra
 * os valores; "Ver no extrato" aplica o mês como período. Legenda + tabela garantem que a
 * leitura não depende só da cor.
 */
@Composable
private fun GraficoEvolucao(meses: List<ResumoMes>, cores: CoresGrafico, onVerMes: (YearMonth) -> Unit) {
    if (meses.isEmpty()) return
    var selecionado by rememberSaveable(meses.size, meses.lastOrNull()?.mes?.toString()) { mutableIntStateOf(meses.size - 1) }
    var tabela by rememberSaveable { mutableStateOf(false) }
    val topo = topoDaEscala(meses.maxOf { maxOf(it.entradasCentavos, it.saidasCentavos) })
    val medidor = rememberTextMeasurer()
    val estiloEixo = TextStyle(fontSize = 10.sp, color = cores.textoMudo)
    val sel = meses[selecionado.coerceIn(meses.indices)]

    Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
        ItemLegenda("Entradas", cores.entradas)
        ItemLegenda("Saídas", cores.saidas)
    }

    val descricao = meses.joinToString("; ") {
        "${it.mes.format(fmtMesLongo)}: entradas ${formatarMoeda(it.entradasCentavos)}, saídas ${formatarMoeda(it.saidasCentavos)}"
    }
    Canvas(
        Modifier.fillMaxWidth().height(180.dp)
            .semantics { contentDescription = descricao }
            .pointerInput(meses.size) {
                detectTapGestures { pos ->
                    val margem = 44.dp.toPx()
                    val larguraGrupo = (size.width - margem) / meses.size
                    val i = ((pos.x - margem) / larguraGrupo).toInt()
                    if (i in meses.indices) selecionado = i
                }
            },
    ) {
        val margemEsq = 44.dp.toPx()
        val margemInf = 18.dp.toPx()
        val alturaPlot = size.height - margemInf
        val larguraPlot = size.width - margemEsq
        val larguraGrupo = larguraPlot / meses.size
        val gap = 2.dp.toPx()
        val larguraBarra = minOf(larguraGrupo * 0.32f, 14.dp.toPx())
        val raio = 4.dp.toPx()

        // Grade discreta em 0, metade e topo, com rótulos à esquerda.
        listOf(0L, topo / 2, topo).forEach { v ->
            val y = alturaPlot - alturaPlot * v / topo
            drawLine(if (v == 0L) cores.eixo else cores.grade, Offset(margemEsq, y), Offset(size.width, y), 1.dp.toPx())
            val t = medidor.measure(formatarMoedaCompacta(v), estiloEixo)
            drawText(t, topLeft = Offset(margemEsq - t.size.width - 4.dp.toPx(), (y - t.size.height / 2).coerceAtLeast(0f)))
        }

        meses.forEachIndexed { i, m ->
            val x0 = margemEsq + larguraGrupo * i
            if (i == selecionado) drawRect(cores.destaque, Offset(x0, 0f), Size(larguraGrupo, alturaPlot))
            val centro = x0 + larguraGrupo / 2
            barraVertical(centro - gap / 2 - larguraBarra, larguraBarra, m.entradasCentavos, topo, alturaPlot, raio, cores.entradas)
            barraVertical(centro + gap / 2, larguraBarra, m.saidasCentavos, topo, alturaPlot, raio, cores.saidas)

            // Com muitos meses, rotula um sim, um não (sempre o selecionado).
            if (meses.size <= 12 || i % 2 == meses.size % 2 || i == selecionado) {
                val t = medidor.measure(
                    m.mes.format(fmtMesCurto).trimEnd('.'),
                    estiloEixo.copy(fontWeight = if (i == selecionado) FontWeight.Bold else FontWeight.Normal),
                )
                drawText(t, topLeft = Offset(centro - t.size.width / 2, alturaPlot + 3.dp.toPx()))
            }
        }
    }

    // Valores do mês selecionado (equivalente ao tooltip, para toque).
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(sel.mes.format(fmtMesLongo).replaceFirstChar { it.uppercase() }, style = MaterialTheme.typography.titleSmall)
        LinhaValor("Entradas", sel.entradasCentavos, cores.entradas)
        LinhaValor("Saídas", sel.saidasCentavos, cores.saidas)
        LinhaValor("Saldo", sel.saldoCentavos, null)
        Row {
            TextButton(onClick = { onVerMes(sel.mes) }) { Text("Ver mês no extrato") }
            TextButton(onClick = { tabela = !tabela }) { Text(if (tabela) "Ocultar tabela" else "Ver tabela") }
        }
    }

    if (tabela) TabelaMeses(meses)
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.barraVertical(
    x: Float, largura: Float, valor: Long, topo: Long, alturaPlot: Float, raio: Float, cor: Color,
) {
    if (valor <= 0) return
    val altura = (alturaPlot * valor / topo).coerceAtLeast(2f)
    val r = minOf(raio, altura, largura / 2)
    // Só o topo arredondado; a base fica reta sobre o eixo.
    val path = Path().apply {
        addRoundRect(
            RoundRect(
                left = x, top = alturaPlot - altura, right = x + largura, bottom = alturaPlot,
                topLeftCornerRadius = CornerRadius(r), topRightCornerRadius = CornerRadius(r),
                bottomLeftCornerRadius = CornerRadius.Zero, bottomRightCornerRadius = CornerRadius.Zero,
            ),
        )
    }
    drawPath(path, cor)
}

@Composable
private fun ItemLegenda(texto: String, cor: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(10.dp).background(cor, RoundedCornerShape(2.dp)))
        Spacer(Modifier.width(6.dp))
        Text(texto, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun LinhaValor(rotulo: String, centavos: Long, cor: Color?) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (cor != null) Box(Modifier.size(8.dp).background(cor, RoundedCornerShape(2.dp))) else Spacer(Modifier.width(8.dp))
        Spacer(Modifier.width(6.dp))
        Text(rotulo, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        Text(formatarMoeda(centavos), style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun TabelaMeses(meses: List<ResumoMes>) {
    Column {
        Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
            Text("Mês", Modifier.weight(1.2f), style = MaterialTheme.typography.labelMedium)
            Text("Entradas", Modifier.weight(1f), style = MaterialTheme.typography.labelMedium, textAlign = TextAlign.End)
            Text("Saídas", Modifier.weight(1f), style = MaterialTheme.typography.labelMedium, textAlign = TextAlign.End)
            Text("Saldo", Modifier.weight(1f), style = MaterialTheme.typography.labelMedium, textAlign = TextAlign.End)
        }
        HorizontalDivider()
        meses.asReversed().forEach { m ->
            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                Text(m.mes.format(DateTimeFormatter.ofPattern("MM/yyyy")), Modifier.weight(1.2f), style = MaterialTheme.typography.bodySmall)
                Text(formatarMoedaCompacta(m.entradasCentavos), Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.End)
                Text(formatarMoedaCompacta(m.saidasCentavos), Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.End)
                Text(formatarMoedaCompacta(m.saldoCentavos), Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.End)
            }
        }
    }
}
