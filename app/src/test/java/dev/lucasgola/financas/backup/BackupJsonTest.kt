package dev.lucasgola.financas.backup

import dev.lucasgola.financas.data.Categoria
import dev.lucasgola.financas.data.Estabelecimento
import dev.lucasgola.financas.data.ItemNota
import dev.lucasgola.financas.data.Lancamento
import dev.lucasgola.financas.data.NotaFiscal
import dev.lucasgola.financas.data.OrigemLancamento
import dev.lucasgola.financas.data.StatusNota
import dev.lucasgola.financas.data.TipoCategoria
import dev.lucasgola.financas.data.TipoLancamento
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal
import java.time.Instant

class BackupJsonTest {

    private val t = Instant.parse("2026-10-02T15:08:31.123Z")

    private val backup = Backup(
        geradoEm = t,
        categorias = listOf(
            Categoria(1, "Alimentação fora", 0xFFEF6C00, TipoCategoria.SAIDA),
            Categoria(7, "Salário", 0xFF00897B, TipoCategoria.ENTRADA, ativa = false),
        ),
        estabelecimentos = listOf(Estabelecimento(3, "58891504001796", "Horizonte \"Restaurantes\" S.A.", "Rua X, 1\nSão Paulo", 1)),
        lancamentos = listOf(
            Lancamento(
                10, TipoLancamento.SAIDA, 8490, t, "Almoço", 1, OrigemLancamento.NFCE, "Outros", 3, null,
                criadoEm = t, atualizadoEm = t.plusSeconds(60),
            ),
            Lancamento(11, TipoLancamento.ENTRADA, 650000, t, "Salário", 7, criadoEm = t, atualizadoEm = t, observacao = "null"),
        ),
        notas = listOf(
            NotaFiscal(
                20, "35261058891504001796650020000075601259531450", "https://x?p=1|3|1", "SP", 7560, 2, t,
                8490, 0, 8490, StatusNota.IMPORTADA, lancamentoId = 10, importadaEm = t,
            ),
            // Pendente: sem lançamento e sem valores.
            NotaFiscal(21, "35261058891504001796650020000075611259531455", "u", "SP", 7561, 2, null, null, 0, null, StatusNota.PENDENTE, "timeout", importadaEm = t),
        ),
        itens = listOf(
            ItemNota(30, 20, 1, "26158", "Crunch Chicken", BigDecimal("4"), "UN", BigDecimal("10.10"), 0, 4040),
            ItemNota(31, 20, 2, "9002", "Batata", BigDecimal("0.432"), "KG", BigDecimal("5.899"), 5, 250, categoriaId = 1),
        ),
    )

    @Test
    fun `ida e volta reproduz exatamente os mesmos dados`() {
        val lido = BackupJson.ler(BackupJson.gerar(backup))
        assertEquals(backup, lido)
        // equals de BigDecimal compara a escala: "10.10" não pode virar "10.1".
        assertEquals("10.10", lido.itens[0].valorUnitario.toPlainString())
        assertEquals(null, lido.lancamentos[0].observacao)
        assertEquals("null", lido.lancamentos[1].observacao) // texto "null" não vira null
        assertEquals(backup.resumo(), lido.resumo())
    }

    @Test
    fun `resumo soma entradas e saidas`() {
        val r = backup.resumo()
        assertEquals(ResumoBackup(lancamentos = 2, notas = 2, itens = 2, entradasCentavos = 650000, saidasCentavos = 8490), r)
    }

    @Test
    fun `rejeita arquivo que nao e backup`() {
        assertInvalido("isto não é json")
        assertInvalido("""{"formato":"outro","versao":1}""")
        assertInvalido("[]")
    }

    @Test
    fun `rejeita backup de versao mais nova`() {
        val json = JSONObject(BackupJson.gerar(backup)).put("versao", BackupJson.VERSAO + 1).toString()
        val e = assertInvalido(json)
        assertTrue(e.message!!.contains("versão mais nova"))
    }

    @Test
    fun `rejeita backup corrompido`() {
        val semCampo = JSONObject(BackupJson.gerar(backup)).apply { remove("itens") }.toString()
        assertInvalido(semCampo)
        val enumInvalido = BackupJson.gerar(backup).replace("\"IMPORTADA\"", "\"QUALQUER\"")
        assertInvalido(enumInvalido)
    }

    private fun assertInvalido(texto: String) = assertThrows(BackupInvalidoException::class.java) { BackupJson.ler(texto) }

    @Test
    fun `nome do arquivo tem a data`() {
        assertEquals("financas-backup-2026-10-08.json", nomeArquivoBackup(java.time.LocalDate.of(2026, 10, 8)))
    }
}
