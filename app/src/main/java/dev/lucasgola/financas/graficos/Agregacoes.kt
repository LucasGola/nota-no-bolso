package dev.lucasgola.financas.graficos

import dev.lucasgola.financas.data.LancamentoComCategoria
import dev.lucasgola.financas.data.TipoLancamento
import dev.lucasgola.financas.filtro.Periodo
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

/** Total por categoria para um tipo (saídas, por padrão), do maior para o menor. */
fun porCategoria(lista: List<LancamentoComCategoria>, tipo: TipoLancamento = TipoLancamento.SAIDA): List<FatiaCategoria> {
    val doTipo = lista.filter { it.lancamento.tipo == tipo }
    val total = doTipo.sumOf { it.lancamento.valorCentavos }
    if (total == 0L) return emptyList()
    return doTipo.groupBy { it.lancamento.categoriaId }
        .map { (id, itens) ->
            val soma = itens.sumOf { it.lancamento.valorCentavos }
            FatiaCategoria(id, itens.first().categoriaNome, itens.first().categoriaCor, soma, soma.toDouble() / total)
        }
        .sortedWith(compareByDescending<FatiaCategoria> { it.totalCentavos }.thenBy { it.nome })
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
