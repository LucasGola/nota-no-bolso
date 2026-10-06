package dev.lucasgola.financas.nfce

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.YearMonth

class ChaveAcessoTest {

    private val chave = "35261058891504001796650020000075601259531450"

    @Test
    fun `decompoe a chave`() {
        val c = ChaveAcesso.of(chave)!!
        assertEquals("35", c.uf)
        assertEquals(YearMonth.of(2026, 10), c.anoMes)
        assertEquals("58891504001796", c.cnpjEmitente)
        assertEquals("65", c.modelo)
        assertEquals(2, c.serie)
        assertEquals(7560L, c.numero)
    }

    @Test
    fun `aceita chave com espacos`() {
        assertNotNull(ChaveAcesso.of("3526 1058 8915 0400 1796 6500 2000 0075 6012 5953 1450"))
    }

    @Test
    fun `rejeita DV errado e tamanho errado`() {
        assertNull(ChaveAcesso.of(chave.dropLast(1) + "1"))
        assertNull(ChaveAcesso.of(chave.dropLast(1)))
        assertNull(ChaveAcesso.of("abc"))
    }

    @Test
    fun `extrai chave de QR v3 codificado`() {
        val url = "https://www.nfce.fazenda.sp.gov.br/NFCeConsultaPublica/Paginas/ConsultaQRCode.aspx?p=$chave%7C3%7C1"
        assertEquals(chave, ChaveAcesso.doQrCode(url)?.valor)
    }

    @Test
    fun `extrai chave de QR v2 com pipe literal`() {
        val url = "https://www.nfce.fazenda.sp.gov.br/qrcode?p=$chave|2|1|1|ABCDEF0123456789"
        assertEquals(chave, ChaveAcesso.doQrCode(url)?.valor)
    }

    @Test
    fun `QR que nao e de NFC-e retorna null`() {
        assertNull(ChaveAcesso.doQrCode("https://example.com/?q=1"))
        assertNull(ChaveAcesso.doQrCode("texto qualquer"))
    }
}
