package dev.lucasgola.financas.graficos

import dev.lucasgola.financas.data.Lancamento
import dev.lucasgola.financas.data.LancamentoComCategoria
import dev.lucasgola.financas.data.TipoLancamento
import dev.lucasgola.financas.filtro.Periodo
import dev.lucasgola.financas.util.ZONA
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth

class AgregacoesTest {

    private var proxId = 1L

    private fun l(
        tipo: TipoLancamento,
        valor: Long,
        data: String,
        categoria: Long = 1,
        nomeCategoria: String = "Mercado",
        estabelecimento: Long? = null,
    ) = LancamentoComCategoria(
        lancamento = Lancamento(
            id = proxId++,
            tipo = tipo,
            valorCentavos = valor,
            dataHora = LocalDateTime.parse(data).atZone(ZONA).toInstant(),
            descricao = "",
            categoriaId = categoria,
            estabelecimentoId = estabelecimento,
        ),
        categoriaNome = nomeCategoria,
        categoriaCor = 0xFF000000 + categoria,
    )

    private val S = TipoLancamento.SAIDA
    private val E = TipoLancamento.ENTRADA

    @Test
    fun `por categoria soma so saidas, ordena e calcula fracao`() {
        val lista = listOf(
            l(S, 3000, "2026-10-01T10:00", 1, "Mercado"),
            l(S, 1000, "2026-10-02T10:00", 2, "Lazer"),
            l(S, 1000, "2026-10-03T10:00", 1, "Mercado"),
            l(E, 99999, "2026-10-04T10:00", 3, "Salário"),
        )
        val r = porCategoria(lista)
        assertEquals(listOf("Mercado", "Lazer"), r.map { it.nome })
        assertEquals(4000L, r[0].totalCentavos)
        assertEquals(0.8, r[0].fracao, 1e-9)
        assertEquals(1.0, r.sumOf { it.fracao }, 1e-9)
    }

    @Test
    fun `por categoria vazio quando nao ha saidas`() {
        assertTrue(porCategoria(listOf(l(E, 100, "2026-10-01T10:00"))).isEmpty())
    }

    @Test
    fun `top estabelecimentos ignora lancamentos sem estabelecimento e limita`() {
        val lista = listOf(
            l(S, 500, "2026-10-01T10:00", estabelecimento = 10),
            l(S, 700, "2026-10-01T11:00", estabelecimento = 20),
            l(S, 400, "2026-10-01T12:00", estabelecimento = 10),
            l(S, 9999, "2026-10-01T13:00"),
        )
        val r = topEstabelecimentos(lista, mapOf(10L to "Padaria", 20L to "Posto"), limite = 1)
        assertEquals(1, r.size)
        assertEquals("Padaria", r[0].nome)
        assertEquals(900L, r[0].totalCentavos)
    }

    @Test
    fun `evolucao inclui meses zerados e usa o fuso de SP`() {
        val meses = listOf(YearMonth.of(2026, 9), YearMonth.of(2026, 10), YearMonth.of(2026, 11))
        val lista = listOf(
            l(S, 100, "2026-09-30T23:30"), // ainda setembro em SP
            l(E, 500, "2026-10-05T12:00"),
            l(S, 200, "2026-10-06T12:00"),
        )
        val r = evolucaoMensal(lista, meses)
        assertEquals(listOf(100L, 200L, 0L), r.map { it.saidasCentavos })
        assertEquals(listOf(0L, 500L, 0L), r.map { it.entradasCentavos })
        assertEquals(300L, r[1].saldoCentavos)
    }

    @Test
    fun `meses da evolucao por tipo de periodo`() {
        val hoje = LocalDate.of(2026, 10, 8)
        assertEquals(
            (5 downTo 0).map { YearMonth.of(2026, 10).minusMonths(it.toLong()) },
            mesesDaEvolucao(Periodo.Mes(YearMonth.of(2026, 10)), hoje),
        )
        assertEquals(12, mesesDaEvolucao(Periodo.Tudo, hoje).size)
        assertEquals(12, mesesDaEvolucao(Periodo.Ano(2026), hoje).size)
        assertEquals(
            listOf(YearMonth.of(2026, 8), YearMonth.of(2026, 9), YearMonth.of(2026, 10)),
            mesesDaEvolucao(Periodo.UltimosDias(60), hoje),
        )
        val longo = Periodo.Personalizado(LocalDate.of(2020, 1, 1), LocalDate.of(2026, 10, 1))
        assertEquals(24, mesesDaEvolucao(longo, hoje).size)
        assertEquals(YearMonth.of(2026, 10), mesesDaEvolucao(longo, hoje).last())
    }
}
