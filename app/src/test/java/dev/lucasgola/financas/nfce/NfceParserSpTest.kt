package dev.lucasgola.financas.nfce

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDateTime

class NfceParserSpTest {

    private fun fixture(nome: String): String =
        javaClass.getResource("/nfce/$nome")!!.readText(Charsets.UTF_8)

    @Test
    fun `restaurante com 3 itens e dois pagamentos`() {
        val nota = NfceParserSp.parse(fixture("sp_restaurante_3itens.html"))

        assertEquals("35261058891504001796650020000075601259531450", nota.chave.valor)
        assertEquals("Horizonte Restaurantes S.A.", nota.emitente.razaoSocial)
        assertEquals("58891504001796", nota.emitente.cnpj)
        assertEquals("Capitão Pacheco e Chaves, 313, Vila Prudente, São Paulo, SP", nota.emitente.endereco)
        assertEquals(7560L, nota.numero)
        assertEquals(2, nota.serie)
        assertEquals(LocalDateTime.of(2026, 10, 2, 12, 8, 19), nota.dataEmissao)

        assertEquals(3, nota.itens.size)
        with(nota.itens[0]) {
            assertEquals(1, ordem)
            assertEquals("26158", codigo)
            assertEquals("Crunch Chicken", descricao)
            assertEquals(0, BigDecimal("4").compareTo(quantidade))
            assertEquals("UN", unidade)
            assertEquals(0, BigDecimal("10.1").compareTo(valorUnitario))
            assertEquals(4040L, valorTotalCentavos)
        }
        assertEquals("Batata Média", nota.itens[1].descricao)
        assertEquals(1870L, nota.itens[1].valorTotalCentavos)
        assertEquals("Coca Zero Lata", nota.itens[2].descricao)

        assertNull(nota.valorBrutoCentavos)
        assertEquals(0L, nota.descontoCentavos)
        assertEquals(8490L, nota.valorAPagarCentavos)
        assertEquals(listOf(Pagamento("Outros", 4670), Pagamento("Outros", 3820)), nota.pagamentos)
        assertTrue(nota.totaisConferem)
    }

    @Test
    fun `pagina sem tabela de itens gera erro claro`() {
        val ex = assertThrows(NfceParseException::class.java) {
            NfceParserSp.parse("<html><body><div id='erro'>Serviço indisponível</div></body></html>")
        }
        assertTrue(ex.message!!.contains("tabela de itens"))
    }

    @Test
    fun `nota cancelada e rejeitada`() {
        assertThrows(NfceParseException::class.java) {
            NfceParserSp.parse(fixture("sp_restaurante_3itens.html").replace("<body >", "<body ><input id='hdfNotaCancelada'>"))
        }
    }
}
