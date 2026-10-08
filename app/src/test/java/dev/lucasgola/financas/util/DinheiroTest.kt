package dev.lucasgola.financas.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.math.BigDecimal

class DinheiroTest {

    @Test
    fun `parse de valores pt-BR para centavos`() {
        assertEquals(123456L, parseCentavosBr("1.234,56"))
        assertEquals(1010L, parseCentavosBr("10,1"))
        assertEquals(500L, parseCentavosBr("R$ 5"))
        assertEquals(8490L, parseCentavosBr(" 84,90 "))
        assertEquals(10L, parseCentavosBr("0,1"))
    }

    @Test
    fun `parse invalido retorna null`() {
        assertNull(parseCentavosBr("NaN"))
        assertNull(parseCentavosBr(""))
        assertNull(parseCentavosBr("12,3,4"))
        assertNull(parseCentavosBr("abc"))
    }

    @Test
    fun `decimal preserva casas de quantidade e valor unitario`() {
        assertEquals(0, BigDecimal("0.432").compareTo(parseDecimalBr("0,432")))
        assertEquals(0, BigDecimal("5.899").compareTo(parseDecimalBr("5,899")))
    }

    @Test
    fun `arredondamento para centavos e meio para cima`() {
        assertEquals(255L, BigDecimal("2.545").paraCentavos())
        assertEquals(254L, BigDecimal("2.544").paraCentavos())
    }

    @Test
    fun `formato compacto para eixos`() {
        assertEquals("R$ 850", formatarMoedaCompacta(85000))
        assertEquals("R$ 1,2 mil", formatarMoedaCompacta(123456))
        assertEquals("R$ 3,4 mi", formatarMoedaCompacta(340000000))
        assertEquals("−R$ 1,5 mil", formatarMoedaCompacta(-150000))
        assertEquals("R$ 0", formatarMoedaCompacta(0))
    }

    @Test
    fun `preco unitario tem no minimo duas casas e preserva as extras`() {
        assertEquals("10,10", formatarPrecoUnitario(BigDecimal("10.1")))
        assertEquals("5,899", formatarPrecoUnitario(BigDecimal("5.899")))
        assertEquals("1.234,00", formatarPrecoUnitario(BigDecimal("1234")))
    }

    @Test
    fun `formatacao`() {
        assertEquals("1234,56", formatarDecimalEdicao(123456))
        assertEquals("0,432", formatarDecimalBr(BigDecimal("0.432")))
        assertEquals("4", formatarDecimalBr(BigDecimal("4")))
        assertEquals("10,1", formatarDecimalBr(BigDecimal("10.10")))
    }
}
