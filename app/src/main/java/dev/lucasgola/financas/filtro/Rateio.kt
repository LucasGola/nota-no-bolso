package dev.lucasgola.financas.filtro

import dev.lucasgola.financas.data.Lancamento
import dev.lucasgola.financas.data.LancamentoComCategoria

/** Item de nota fiscal reduzido ao que importa para repartir o valor entre categorias. */
data class ItemRateio(val lancamentoId: Long, val categoriaId: Long?, val valorCentavos: Long)

/**
 * Quanto do lançamento pertence a cada categoria.
 *
 * Classificar itens é opcional: item sem categoria fica na categoria do lançamento. Cada item
 * classificado leva uma parte proporcional ao seu valor (item ÷ soma dos itens × valor pago), de modo
 * que o desconto da nota é repartido entre todos e as partes sempre somam exatamente o valor pago.
 * O arredondamento (centavos) fica com a categoria do lançamento.
 */
fun ratear(valorCentavos: Long, categoriaId: Long, itens: List<ItemRateio>): Map<Long, Long> {
    val soma = itens.sumOf { maxOf(0L, it.valorCentavos) }
    val classificados = itens.filter { it.categoriaId != null && it.categoriaId != categoriaId }
    if (soma <= 0L || classificados.isEmpty()) return mapOf(categoriaId to valorCentavos)

    val partes = LinkedHashMap<Long, Long>()
    partes[categoriaId] = 0L
    classificados.forEach { item ->
        val parte = maxOf(0L, item.valorCentavos) * valorCentavos / soma
        partes.merge(item.categoriaId!!, parte, Long::plus)
    }
    partes[categoriaId] = valorCentavos - (partes.values.sum())
    return partes.filterValues { it != 0L }.ifEmpty { mapOf(categoriaId to valorCentavos) }
}

/** Itens classificados por lançamento; lançamento sem item classificado não aparece no mapa. */
class Rateio(private val itensPorLancamento: Map<Long, List<ItemRateio>>) {

    constructor(itens: List<ItemRateio>) : this(itens.groupBy { it.lancamentoId })

    fun partes(l: Lancamento): Map<Long, Long> =
        ratear(l.valorCentavos, l.categoriaId, itensPorLancamento[l.id].orEmpty())

    /** Parte do lançamento que pertence às [categorias]; sem restrição de categoria, o valor inteiro. */
    fun valorNasCategorias(l: Lancamento, categorias: Set<Long>): Long =
        if (categorias.isEmpty()) l.valorCentavos
        else partes(l).filterKeys { it in categorias }.values.sum()

    /** O lançamento tem itens classificados em outras categorias (o valor está repartido). */
    fun repartido(l: Lancamento): Boolean = partes(l).size > 1 || l.categoriaId !in partes(l)

    companion object {
        val VAZIO = Rateio(emptyMap())
    }
}

/**
 * Com filtro de categoria, cada lançamento passa a valer só a parte dessas categorias
 * (ex.: filtrando "Carne", a compra de R$ 300 no mercado vale os R$ 40 da carne) e os que
 * ficam com zero saem. Sem filtro de categoria, a lista volta inalterada.
 */
fun List<LancamentoComCategoria>.comValorNasCategorias(rateio: Rateio, categorias: Set<Long>): List<LancamentoComCategoria> =
    if (categorias.isEmpty()) this
    else mapNotNull { item ->
        val valor = rateio.valorNasCategorias(item.lancamento, categorias)
        if (valor == 0L) null else item.copy(lancamento = item.lancamento.copy(valorCentavos = valor))
    }
