package dev.lucasgola.financas.ui.categorias

import dev.lucasgola.financas.data.Categoria
import dev.lucasgola.financas.data.TipoCategoria
import org.junit.Assert.assertEquals
import org.junit.Test

class FiltroCategoriasTest {

    private val lista = listOf(
        Categoria(1, "Mercado", 0, TipoCategoria.SAIDA),
        Categoria(2, "Salário", 0, TipoCategoria.ENTRADA),
        Categoria(3, "Outros", 0, TipoCategoria.AMBOS),
        Categoria(4, "Açaí", 0, TipoCategoria.ITEM),
        Categoria(5, "Carne", 0, TipoCategoria.ITEM),
    )

    private fun nomes(tipo: FiltroTipoCategoria, busca: String = "") = filtrarCategorias(lista, tipo, busca).map { it.nome }

    @Test
    fun `filtra por tipo, com entrada e saida incluindo as dos dois`() {
        assertEquals(lista.map { it.nome }, nomes(FiltroTipoCategoria.TODAS))
        assertEquals(listOf("Salário", "Outros"), nomes(FiltroTipoCategoria.ENTRADA))
        assertEquals(listOf("Mercado", "Outros"), nomes(FiltroTipoCategoria.SAIDA))
        assertEquals(listOf("Açaí", "Carne"), nomes(FiltroTipoCategoria.ITEM))
    }

    @Test
    fun `busca ignora acentos e maiusculas e combina com o tipo`() {
        assertEquals(listOf("Açaí"), nomes(FiltroTipoCategoria.TODAS, "ACAI"))
        assertEquals(listOf("Salário"), nomes(FiltroTipoCategoria.TODAS, " salario "))
        assertEquals(emptyList<String>(), nomes(FiltroTipoCategoria.SAIDA, "carne"))
        assertEquals(listOf("Carne"), nomes(FiltroTipoCategoria.ITEM, "car"))
    }

    @Test
    fun `categoria nova nasce no tipo do filtro`() {
        assertEquals(TipoCategoria.ITEM, FiltroTipoCategoria.ITEM.tipoParaNova)
        assertEquals(TipoCategoria.ENTRADA, FiltroTipoCategoria.ENTRADA.tipoParaNova)
        assertEquals(TipoCategoria.SAIDA, FiltroTipoCategoria.TODAS.tipoParaNova)
    }
}
