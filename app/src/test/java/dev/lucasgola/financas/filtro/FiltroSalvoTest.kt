package dev.lucasgola.financas.filtro

import dev.lucasgola.financas.data.OrigemLancamento
import dev.lucasgola.financas.data.TipoLancamento
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

class FiltroSalvoTest {

    private val hoje = LocalDate.of(2026, 10, 15)
    private val padrao = Filtro(periodo = Periodo.Mes(YearMonth.of(2026, 10)))

    private fun idaEVolta(f: Filtro, dia: LocalDate = hoje) = FiltroSalvo.decodificar(FiltroSalvo.codificar(f), dia)

    @Test
    fun `restricoes e periodos sobrevivem`() {
        val f = Filtro(
            periodo = Periodo.Personalizado(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 3, 31)),
            tipo = TipoLancamento.SAIDA,
            categorias = setOf(1, 7),
            estabelecimentos = setOf(3),
            origem = OrigemLancamento.NFCE,
            valorMinCentavos = 1000,
            valorMaxCentavos = null,
            busca = "pão",
        )
        assertEquals(f, idaEVolta(f))
        listOf(Periodo.UltimosDias(30), Periodo.Ano(2025), Periodo.Tudo).forEach {
            assertEquals(Filtro(periodo = it), idaEVolta(Filtro(periodo = it)))
        }
    }

    @Test
    fun `mes volta para o mes atual`() {
        val setembro = Filtro(periodo = Periodo.Mes(YearMonth.of(2026, 9)), tipo = TipoLancamento.ENTRADA)
        assertEquals(padrao.copy(tipo = TipoLancamento.ENTRADA), idaEVolta(setembro))
        assertEquals(Periodo.Mes(YearMonth.of(2026, 11)), idaEVolta(setembro, LocalDate.of(2026, 11, 2)).periodo)
    }

    @Test
    fun `sem nada salvo ou texto invalido usa o padrao`() {
        assertEquals(padrao, FiltroSalvo.decodificar(null, hoje))
        assertEquals(padrao, FiltroSalvo.decodificar("lixo", hoje))
        assertEquals(padrao, FiltroSalvo.decodificar("""{"periodo":{"tipo":"tudo"},"tipo":"OUTRO"}""", hoje))
        assertEquals(
            padrao,
            FiltroSalvo.decodificar("""{"periodo":{"tipo":"personalizado","de":"x","ate":"y"},"categorias":[],"estabelecimentos":[]}""", hoje),
        )
    }
}
