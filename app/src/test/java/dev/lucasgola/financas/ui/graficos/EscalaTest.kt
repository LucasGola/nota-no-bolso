package dev.lucasgola.financas.ui.graficos

import org.junit.Assert.assertEquals
import org.junit.Test

class EscalaTest {
    @Test
    fun `topo da escala fica logo acima do maximo`() {
        assertEquals(800_000L, topoDaEscala(650_000))   // R$ 6.500 → R$ 8 mil
        assertEquals(1_000_000L, topoDaEscala(1_000_000)) // exato
        assertEquals(150_000L, topoDaEscala(123_456))   // R$ 1.234,56 → R$ 1,5 mil
        assertEquals(10_000L, topoDaEscala(0))           // sem dados: R$ 100
    }
}
