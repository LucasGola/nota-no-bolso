package dev.lucasgola.financas.data

import org.junit.Assert.assertEquals
import org.junit.Test

class TipoCategoriaTest {

    @Test
    fun `categoria de item nunca serve para lancamento`() {
        assertEquals(false, TipoCategoria.ITEM.aceita(TipoLancamento.SAIDA))
        assertEquals(false, TipoCategoria.ITEM.aceita(TipoLancamento.ENTRADA))
    }

    @Test
    fun `itens aceitam categorias de item e de saida, nunca so de entrada`() {
        assertEquals(
            listOf(TipoCategoria.SAIDA, TipoCategoria.AMBOS, TipoCategoria.ITEM),
            TipoCategoria.entries.filter { it.serveParaItem },
        )
    }
}
