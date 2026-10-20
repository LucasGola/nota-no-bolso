package dev.lucasgola.financas.filtro

import dev.lucasgola.financas.data.Categoria
import dev.lucasgola.financas.data.Lancamento
import dev.lucasgola.financas.data.LancamentoComCategoria
import dev.lucasgola.financas.data.TipoCategoria
import dev.lucasgola.financas.data.TipoLancamento
import dev.lucasgola.financas.graficos.porCategoria
import dev.lucasgola.financas.graficos.porCategoriaDeItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class RateioTest {

    private val MERCADO = 1L
    private val CARNE = 2L
    private val LIMPEZA = 3L

    private fun item(categoria: Long?, valor: Long, lancamento: Long = 1) = ItemRateio(lancamento, categoria, valor)

    @Test
    fun `sem item classificado tudo fica na categoria do lancamento`() {
        assertEquals(mapOf(MERCADO to 30000L), ratear(30000, MERCADO, listOf(item(null, 20000), item(null, 10000))))
        assertEquals(mapOf(MERCADO to 30000L), ratear(30000, MERCADO, emptyList()))
    }

    @Test
    fun `item classificado leva a sua parte e o resto fica no lancamento`() {
        val partes = ratear(30000, MERCADO, listOf(item(CARNE, 4000), item(null, 26000)))
        assertEquals(mapOf(MERCADO to 26000L, CARNE to 4000L), partes)
    }

    @Test
    fun `desconto da nota e repartido proporcionalmente`() {
        // Itens somam R$ 100, pago R$ 90 (10% de desconto): a carne de R$ 40 conta R$ 36.
        val partes = ratear(9000, MERCADO, listOf(item(CARNE, 4000), item(LIMPEZA, 1000), item(null, 5000)))
        assertEquals(3600L, partes[CARNE])
        assertEquals(900L, partes[LIMPEZA])
        assertEquals(4500L, partes[MERCADO])
        assertEquals(9000L, partes.values.sum())
    }

    @Test
    fun `arredondamento fica com a categoria do lancamento e a soma fecha`() {
        val partes = ratear(1000, MERCADO, listOf(item(CARNE, 1), item(CARNE, 1), item(null, 1)))
        assertEquals(666L, partes[CARNE]) // 333 + 333
        assertEquals(334L, partes[MERCADO])
        assertEquals(1000L, partes.values.sum())
    }

    @Test
    fun `todos os itens em outra categoria tiram o lancamento da sua propria`() {
        val partes = ratear(5000, MERCADO, listOf(item(CARNE, 3000), item(CARNE, 2000)))
        assertEquals(mapOf(CARNE to 5000L), partes)
    }

    @Test
    fun `item na mesma categoria do lancamento nao conta como classificado`() {
        assertEquals(mapOf(MERCADO to 5000L), ratear(5000, MERCADO, listOf(item(MERCADO, 3000), item(null, 2000))))
    }

    // ---- Uso com a lista de lançamentos ----

    private fun lanc(id: Long, valor: Long, categoria: Long, nome: String) = LancamentoComCategoria(
        Lancamento(id = id, tipo = TipoLancamento.SAIDA, valorCentavos = valor, dataHora = Instant.EPOCH, descricao = "", categoriaId = categoria),
        categoriaNome = nome, categoriaCor = 0,
    )

    private val compra = lanc(1, 30000, MERCADO, "Mercado")
    private val faxina = lanc(2, 5000, LIMPEZA, "Limpeza")
    private val rateio = Rateio(listOf(item(CARNE, 4000, lancamento = 1), item(null, 26000, lancamento = 1)))
    private val categorias = listOf(
        Categoria(MERCADO, "Mercado", 0, TipoCategoria.SAIDA),
        Categoria(CARNE, "Carne", 0, TipoCategoria.SAIDA),
        Categoria(LIMPEZA, "Limpeza", 0, TipoCategoria.SAIDA),
    ).associateBy { it.id }

    @Test
    fun `filtro de categoria vale so a parte da categoria`() {
        val lista = listOf(compra, faxina)
        val carne = lista.comValorNasCategorias(rateio, setOf(CARNE))
        assertEquals(listOf(1L), carne.map { it.lancamento.id })
        assertEquals(4000L, carne.single().lancamento.valorCentavos)

        val mercado = lista.comValorNasCategorias(rateio, setOf(MERCADO))
        assertEquals(26000L, mercado.single().lancamento.valorCentavos)

        assertEquals(lista, lista.comValorNasCategorias(rateio, emptySet()))
    }

    @Test
    fun `grafico por categoria reparte as notas`() {
        val r = porCategoria(listOf(compra, faxina), TipoLancamento.SAIDA, rateio, categorias)
        assertEquals(listOf("Mercado" to 26000L, "Limpeza" to 5000L, "Carne" to 4000L), r.map { it.nome to it.totalCentavos })
        assertEquals(1.0, r.sumOf { it.fracao }, 1e-9)

        val soCarne = porCategoria(listOf(compra, faxina), TipoLancamento.SAIDA, rateio, categorias, restringirA = setOf(CARNE))
        assertEquals(listOf("Carne" to 4000L), soCarne.map { it.nome to it.totalCentavos })
    }

    @Test
    // ---- Categorias só de itens: dimensão à parte ----

    private val carneDeItem = categorias + (CARNE to Categoria(CARNE, "Carne", 0, TipoCategoria.ITEM))
    private val rateioComItem = Rateio(
        listOf(item(CARNE, 4000, lancamento = 1), item(null, 26000, lancamento = 1)),
        deItem = setOf(CARNE),
    )

    @Test
    fun `categoria so de itens nao tira nada da categoria geral`() {
        assertEquals(mapOf(MERCADO to 30000L), rateioComItem.partes(compra.lancamento))
        assertEquals(mapOf(CARNE to 4000L, null to 26000L), rateioComItem.partesDeItem(compra.lancamento))
        assertEquals(emptyMap<Long?, Long>(), rateioComItem.partesDeItem(faxina.lancamento)) // sem itens
    }

    @Test
    fun `filtro conta cada item uma vez, mesmo com categoria geral e de item juntas`() {
        assertEquals(4000L, rateioComItem.valorNasCategorias(compra.lancamento, setOf(CARNE)))
        assertEquals(30000L, rateioComItem.valorNasCategorias(compra.lancamento, setOf(MERCADO)))
        assertEquals(30000L, rateioComItem.valorNasCategorias(compra.lancamento, setOf(MERCADO, CARNE)))
        assertEquals(0L, rateioComItem.valorNasCategorias(compra.lancamento, setOf(LIMPEZA)))
    }

    @Test
    fun `grafico geral nao mostra categorias so de itens`() {
        val r = porCategoria(listOf(compra, faxina), TipoLancamento.SAIDA, rateioComItem, carneDeItem)
        assertEquals(listOf("Mercado" to 30000L, "Limpeza" to 5000L), r.map { it.nome to it.totalCentavos })
    }

    @Test
    fun `grafico de itens tem sem categoria por ultimo`() {
        val r = porCategoriaDeItem(listOf(compra, faxina), rateioComItem, carneDeItem)
        assertEquals(listOf("Carne" to 4000L, "Sem categoria" to 26000L), r.map { it.nome to it.totalCentavos })
        assertEquals(null, r.last().categoriaId)
        assertEquals(1.0, r.sumOf { it.fracao }, 1e-9)

        // Filtrando por uma categoria de item, só ela aparece; nenhuma categoria de item cadastrada: painel vazio.
        assertEquals(listOf("Carne"), porCategoriaDeItem(listOf(compra), rateioComItem, carneDeItem, setOf(CARNE)).map { it.nome })
        assertEquals(emptyList<Any>(), porCategoriaDeItem(listOf(compra), rateioComItem, categorias))
    }

    @Test
    fun `sabe se o lancamento esta repartido`() {
        assertTrue(rateio.repartido(compra.lancamento))
        assertFalse(rateio.repartido(faxina.lancamento))
    }
}
