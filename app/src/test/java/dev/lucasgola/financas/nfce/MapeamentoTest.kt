package dev.lucasgola.financas.nfce

import dev.lucasgola.financas.data.OrigemLancamento
import dev.lucasgola.financas.data.StatusNota
import dev.lucasgola.financas.data.TipoLancamento
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.math.BigDecimal
import java.time.Instant

class MapeamentoTest {

    private val nota = NfceParserSp.parse(
        javaClass.getResource("/nfce/sp_restaurante_3itens.html")!!.readText(Charsets.UTF_8)
    )

    @Test
    fun `data de emissao e convertida no fuso de Sao Paulo`() {
        // 02/10/2026 12:08:19 em SP (UTC-3) = 15:08:19 UTC
        assertEquals(Instant.parse("2026-10-02T15:08:19Z"), nota.dataEmissaoInstant)
    }

    @Test
    fun `lancamento e saida com o valor pago`() {
        val l = nota.paraLancamento(categoriaId = 7, descricao = "Almoço", dataHora = nota.dataEmissaoInstant, estabelecimentoId = 3)
        assertEquals(TipoLancamento.SAIDA, l.tipo)
        assertEquals(8490L, l.valorCentavos)
        assertEquals(OrigemLancamento.NFCE, l.origem)
        assertEquals("Outros", l.formaPagamento)
        assertEquals(3L, l.estabelecimentoId)
    }

    @Test
    fun `nota fiscal importada guarda totais e vinculo`() {
        val n = nota.paraNotaFiscal(urlQr = "url", lancamentoId = 10)
        assertEquals(StatusNota.IMPORTADA, n.status)
        assertEquals(10L, n.lancamentoId)
        assertEquals(8490L, n.valorTotalCentavos)
        assertEquals(7560L, n.numero)
        assertNull(n.erroMsg)
    }

    @Test
    fun `itens preservam quantidade e valor unitario sem arredondar`() {
        val itens = nota.paraItens(notaId = 5)
        assertEquals(3, itens.size)
        assertEquals(5L, itens[0].notaId)
        assertEquals(0, BigDecimal("10.1").compareTo(itens[0].valorUnitario))
        assertEquals(4040L, itens[0].valorTotalCentavos)
    }

    @Test
    fun `pendente usa apenas dados da chave`() {
        val chave = ChaveAcesso.of("35261058891504001796650020000075601259531450")!!
        val p = notaPendente(chave, "url", "sem internet")
        assertEquals(StatusNota.PENDENTE, p.status)
        assertEquals(7560L, p.numero)
        assertNull(p.valorTotalCentavos)
        assertNull(p.lancamentoId)
    }

    @Test
    fun `url montada a partir da chave aponta para a consulta de SP`() {
        val chave = ChaveAcesso.of("35261058891504001796650020000075601259531450")!!
        val url = NfceService.urlConsultaSp(chave)
        assertEquals(chave, ChaveAcesso.doQrCode(url))
    }
}
