package dev.lucasgola.financas.graficos

import dev.lucasgola.financas.data.Categoria
import dev.lucasgola.financas.data.LancamentoComCategoria
import dev.lucasgola.financas.data.TipoCategoria
import dev.lucasgola.financas.data.TipoLancamento
import dev.lucasgola.financas.filtro.Periodo
import dev.lucasgola.financas.filtro.Rateio
import dev.lucasgola.financas.util.ZONA
import java.time.LocalDate
import java.time.YearMonth

// Agregações puras para os gráficos (sem Android, testáveis na JVM).

data class FatiaCategoria(
    val categoriaId: Long,
    val nome: String,
    val cor: Long,
    val totalCentavos: Long,
    /** 0..1 em relação ao total do tipo no período. */
    val fracao: Double,
)

data class FatiaEstabelecimento(val estabelecimentoId: Long, val nome: String, val totalCentavos: Long)

data class ResumoMes(val mes: YearMonth, val entradasCentavos: Long, val saidasCentavos: Long) {
    val saldoCentavos: Long get() = entradasCentavos - saidasCentavos
}

/**
 * Total por categoria para um tipo (saídas, por padrão), do maior para o menor.
 * Notas com itens classificados são repartidas pelo [rateio]; com [restringirA], só essas
 * categorias aparecem (o resto da nota, de outra categoria, não entra).
 */
fun porCategoria(
    lista: List<LancamentoComCategoria>,
    tipo: TipoLancamento = TipoLancamento.SAIDA,
    rateio: Rateio = Rateio.VAZIO,
    categorias: Map<Long, Categoria> = emptyMap(),
    restringirA: Set<Long> = emptySet(),
): List<FatiaCategoria> {
    val totais = LinkedHashMap<Long, Long>()
    val nomeCor = HashMap<Long, Pair<String, Long>>()
    lista.filter { it.lancamento.tipo == tipo }.forEach { item ->
        nomeCor.putIfAbsent(item.lancamento.categoriaId, item.categoriaNome to item.categoriaCor)
        rateio.partes(item.lancamento).forEach { (id, valor) ->
            if (restringirA.isEmpty() || id in restringirA) totais.merge(id, valor, Long::plus)
        }
    }
    val total = totais.values.sum()
    if (total == 0L) return emptyList()
    return totais.map { (id, soma) ->
        val (nome, cor) = categorias[id]?.let { it.nome to it.cor } ?: nomeCor[id] ?: ("Categoria" to 0xFF757575)
        FatiaCategoria(id, nome, cor, soma, soma.toDouble() / total)
    }.sortedWith(compareByDescending<FatiaCategoria> { it.totalCentavos }.thenBy { it.nome })
}

/**
 * Gastos só nas categorias "só itens" (ex.: Carne, Limpeza), a partir das partes repartidas das notas.
 * Com filtro de categoria, só as categorias de item que estão no filtro. Vazio quando nada foi classificado.
 */
fun porCategoriaDeItem(
    lista: List<LancamentoComCategoria>,
    rateio: Rateio,
    categorias: Map<Long, Categoria>,
    filtroCategorias: Set<Long> = emptySet(),
): List<FatiaCategoria> {
    val deItem = categorias.values.filter { it.tipo == TipoCategoria.ITEM }.map { it.id }.toSet()
    val alvo = if (filtroCategorias.isEmpty()) deItem else deItem intersect filtroCategorias
    // Conjunto vazio em porCategoria significaria "sem restrição"; aqui significa "nada a mostrar".
    if (alvo.isEmpty()) return emptyList()
    return porCategoria(lista, TipoLancamento.SAIDA, rateio, categorias, alvo)
}

data class FatiaMetodo(
    /** null = lançamentos sem método. */
    val metodoId: Long?,
    val nome: String,
    val totalCentavos: Long,
    val fracao: Double,
)

/** Total por método de pagamento para um tipo, do maior para o menor; "Sem método" agrupa os demais. */
fun porMetodo(
    lista: List<LancamentoComCategoria>,
    metodos: Map<Long, String>,
    tipo: TipoLancamento = TipoLancamento.SAIDA,
): List<FatiaMetodo> {
    val doTipo = lista.filter { it.lancamento.tipo == tipo }
    val total = doTipo.sumOf { it.lancamento.valorCentavos }
    if (total == 0L) return emptyList()
    return doTipo.groupBy { it.lancamento.metodoPagamentoId }
        .map { (id, itens) ->
            val soma = itens.sumOf { it.lancamento.valorCentavos }
            FatiaMetodo(id, id?.let(metodos::get) ?: "Sem método", soma, soma.toDouble() / total)
        }
        // "Sem método" vai sempre por último, para não roubar o destaque do maior método real.
        .sortedWith(compareBy<FatiaMetodo> { it.metodoId == null }.thenByDescending { it.totalCentavos }.thenBy { it.nome })
}

/** Saídas por estabelecimento (só lançamentos vinculados a um), maiores primeiro. */
fun topEstabelecimentos(
    lista: List<LancamentoComCategoria>,
    nomes: Map<Long, String>,
    limite: Int = 10,
): List<FatiaEstabelecimento> =
    lista.filter { it.lancamento.tipo == TipoLancamento.SAIDA && it.lancamento.estabelecimentoId != null }
        .groupBy { it.lancamento.estabelecimentoId!! }
        .map { (id, itens) -> FatiaEstabelecimento(id, nomes[id] ?: "Estabelecimento", itens.sumOf { it.lancamento.valorCentavos }) }
        .sortedWith(compareByDescending<FatiaEstabelecimento> { it.totalCentavos }.thenBy { it.nome })
        .take(limite)

/** Entradas e saídas mês a mês, incluindo meses sem movimento (zerados). */
fun evolucaoMensal(lista: List<LancamentoComCategoria>, meses: List<YearMonth>): List<ResumoMes> {
    val porMes = lista.groupBy { YearMonth.from(it.lancamento.dataHora.atZone(ZONA)) }
    return meses.map { m ->
        val doMes = porMes[m].orEmpty()
        ResumoMes(
            mes = m,
            entradasCentavos = doMes.filter { it.lancamento.tipo == TipoLancamento.ENTRADA }.sumOf { it.lancamento.valorCentavos },
            saidasCentavos = doMes.filter { it.lancamento.tipo == TipoLancamento.SAIDA }.sumOf { it.lancamento.valorCentavos },
        )
    }
}

/**
 * Meses exibidos na evolução: o período de um mês só não mostra tendência, então vira
 * os 6 meses que terminam nele; "todo o período" vira os últimos 12 meses; os demais
 * usam os meses que cobrem — no máximo 24, os mais recentes.
 */
fun mesesDaEvolucao(periodo: Periodo, hoje: LocalDate): List<YearMonth> {
    val (inicio, fim) = when (periodo) {
        is Periodo.Mes -> periodo.mes.minusMonths(5) to periodo.mes
        Periodo.Tudo -> YearMonth.from(hoje).minusMonths(11) to YearMonth.from(hoje)
        else -> {
            val i = periodo.intervalo(hoje)!!
            YearMonth.from(i.inicio) to YearMonth.from(i.fim)
        }
    }
    val meses = generateSequence(inicio) { it.plusMonths(1) }.takeWhile { !it.isAfter(fim) }.toList()
    return meses.takeLast(24)
}
