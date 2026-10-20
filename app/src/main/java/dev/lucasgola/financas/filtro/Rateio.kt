package dev.lucasgola.financas.filtro

import dev.lucasgola.financas.data.Categoria
import dev.lucasgola.financas.data.Lancamento
import dev.lucasgola.financas.data.LancamentoComCategoria
import dev.lucasgola.financas.data.TipoCategoria

/** Item de nota fiscal reduzido ao que importa para repartir o valor entre categorias. */
data class ItemRateio(val lancamentoId: Long, val categoriaId: Long?, val valorCentavos: Long)

/**
 * Valor pago repartido entre os itens: cada item leva item ÷ soma dos itens × valor pago, de modo que
 * o desconto da nota é dividido entre todos. O [resto] (centavos do arredondamento) fica com o lançamento.
 */
private class Divisao(val porItem: List<Pair<ItemRateio, Long>>, val resto: Long)

private fun dividir(valorCentavos: Long, itens: List<ItemRateio>): Divisao? {
    val soma = itens.sumOf { maxOf(0L, it.valorCentavos) }
    if (soma <= 0L) return null
    val porItem = itens.map { it to maxOf(0L, it.valorCentavos) * valorCentavos / soma }
    return Divisao(porItem, valorCentavos - porItem.sumOf { it.second })
}

/**
 * Quanto do lançamento pertence a cada categoria **geral** (as de gasto/entrada).
 *
 * Item classificado numa categoria de gasto leva a sua parte para ela; item sem categoria, ou numa
 * categoria "só itens" ([deItem]), fica na categoria do lançamento. As categorias só de itens são uma
 * dimensão à parte (ver [ratearItens]): classificar a carne como "Carne" não tira nada do Mercado.
 */
fun ratear(valorCentavos: Long, categoriaId: Long, itens: List<ItemRateio>, deItem: Set<Long> = emptySet()): Map<Long, Long> {
    val d = dividir(valorCentavos, itens) ?: return mapOf(categoriaId to valorCentavos)
    val partes = LinkedHashMap<Long, Long>()
    d.porItem.forEach { (item, valor) -> partes.merge(item.geral(categoriaId, deItem), valor, Long::plus) }
    partes.merge(categoriaId, d.resto, Long::plus)
    return partes.filterValues { it != 0L }.ifEmpty { mapOf(categoriaId to valorCentavos) }
}

/**
 * Quanto do lançamento pertence a cada categoria **só de itens**; a chave null ("sem categoria") junta
 * os itens não classificados nelas e o arredondamento. Lançamento sem itens não tem divisão (mapa vazio).
 */
fun ratearItens(valorCentavos: Long, itens: List<ItemRateio>, deItem: Set<Long>): Map<Long?, Long> {
    val d = dividir(valorCentavos, itens) ?: return emptyMap()
    val partes = LinkedHashMap<Long?, Long>()
    d.porItem.forEach { (item, valor) -> partes.merge(item.deItem(deItem), valor, Long::plus) }
    partes.merge(null, d.resto, Long::plus)
    return partes.filterValues { it != 0L }
}

private fun ItemRateio.geral(categoriaDoLancamento: Long, deItem: Set<Long>): Long =
    categoriaId?.takeIf { it !in deItem } ?: categoriaDoLancamento

private fun ItemRateio.deItem(deItem: Set<Long>): Long? = categoriaId?.takeIf { it in deItem }

/**
 * Itens de todas as notas, por lançamento, e quais categorias são "só itens" ([deItem]).
 * Lançamento sem nota (ou nota sem itens) não aparece no mapa.
 */
class Rateio(private val itensPorLancamento: Map<Long, List<ItemRateio>>, private val deItem: Set<Long> = emptySet()) {

    constructor(itens: List<ItemRateio>, deItem: Set<Long> = emptySet()) : this(itens.groupBy { it.lancamentoId }, deItem)

    private fun itens(l: Lancamento) = itensPorLancamento[l.id].orEmpty()

    /** Divisão entre categorias gerais (ver [ratear]). */
    fun partes(l: Lancamento): Map<Long, Long> = ratear(l.valorCentavos, l.categoriaId, itens(l), deItem)

    /** Divisão entre categorias só de itens, com null = sem categoria (ver [ratearItens]). */
    fun partesDeItem(l: Lancamento): Map<Long?, Long> = ratearItens(l.valorCentavos, itens(l), deItem)

    /**
     * Parte do lançamento que pertence às [categorias] (gerais ou só de itens, misturadas ou não);
     * sem restrição de categoria, o valor inteiro. Cada item conta uma vez só, mesmo que a categoria
     * geral e a de item dele estejam ambas no filtro.
     */
    fun valorNasCategorias(l: Lancamento, categorias: Set<Long>): Long {
        if (categorias.isEmpty()) return l.valorCentavos
        val d = dividir(l.valorCentavos, itens(l)) ?: return if (l.categoriaId in categorias) l.valorCentavos else 0L
        val dosItens = d.porItem.sumOf { (item, valor) ->
            if (item.geral(l.categoriaId, deItem) in categorias || item.deItem(deItem) in categorias) valor else 0L
        }
        return dosItens + if (l.categoriaId in categorias) d.resto else 0L
    }

    /** O lançamento tem itens em outras categorias gerais (o valor está repartido). */
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

/** Ids das categorias "só itens", que o [Rateio] trata como dimensão à parte. */
fun Iterable<Categoria>.idsDeItem(): Set<Long> = filter { it.tipo == TipoCategoria.ITEM }.map { it.id }.toSet()
