package dev.lucasgola.financas.filtro

import dev.lucasgola.financas.data.OrigemLancamento
import dev.lucasgola.financas.data.TipoLancamento
import dev.lucasgola.financas.util.LOCALE_BR
import dev.lucasgola.financas.util.ZONA
import dev.lucasgola.financas.util.formatar
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter

/** Intervalo de datas locais (fuso de SP), fim inclusivo. */
data class Intervalo(val inicio: LocalDate, val fim: LocalDate) {
    /** [início, fim) em UTC, para comparar com as colunas gravadas como Instant. */
    fun emInstants(): Pair<Instant, Instant> =
        inicio.atStartOfDay(ZONA).toInstant() to fim.plusDays(1).atStartOfDay(ZONA).toInstant()
}

sealed interface Periodo {
    /** Intervalo concreto para "hoje"; null = sem limite de data. */
    fun intervalo(hoje: LocalDate): Intervalo?
    fun rotulo(hoje: LocalDate): String

    data class Mes(val mes: YearMonth) : Periodo {
        override fun intervalo(hoje: LocalDate) = Intervalo(mes.atDay(1), mes.atEndOfMonth())
        override fun rotulo(hoje: LocalDate): String =
            mes.format(fmtMes).replaceFirstChar { it.uppercase() } + if (mes.year == hoje.year) "" else " ${mes.year}"
    }

    data class UltimosDias(val dias: Int) : Periodo {
        override fun intervalo(hoje: LocalDate) = Intervalo(hoje.minusDays(dias - 1L), hoje)
        override fun rotulo(hoje: LocalDate) = "Últimos $dias dias"
    }

    data class Ano(val ano: Int) : Periodo {
        override fun intervalo(hoje: LocalDate) = Intervalo(LocalDate.of(ano, 1, 1), LocalDate.of(ano, 12, 31))
        override fun rotulo(hoje: LocalDate) = "Ano $ano"
    }

    data class Personalizado(val de: LocalDate, val ate: LocalDate) : Periodo {
        override fun intervalo(hoje: LocalDate) = Intervalo(de, ate)
        override fun rotulo(hoje: LocalDate) = "${de.formatar()} – ${ate.formatar()}"
    }

    data object Tudo : Periodo {
        override fun intervalo(hoje: LocalDate): Intervalo? = null
        override fun rotulo(hoje: LocalDate) = "Todo o período"
    }

    companion object {
        private val fmtMes = DateTimeFormatter.ofPattern("MMMM", LOCALE_BR)
        fun mesAtual(hoje: LocalDate = LocalDate.now(ZONA)) = Mes(YearMonth.from(hoje))
    }
}

/**
 * Filtro único do app: usado pelo extrato, pelos gráficos e pela exportação
 * ("exportar o que estou vendo"). Listas vazias = sem restrição.
 */
data class Filtro(
    val periodo: Periodo = Periodo.mesAtual(),
    val tipo: TipoLancamento? = null,
    val categorias: Set<Long> = emptySet(),
    val estabelecimentos: Set<Long> = emptySet(),
    val origem: OrigemLancamento? = null,
    val valorMinCentavos: Long? = null,
    val valorMaxCentavos: Long? = null,
    val busca: String = "",
) {
    /** Algo além do período padrão está restringindo o resultado. */
    val temRestricoes: Boolean
        get() = tipo != null || categorias.isNotEmpty() || estabelecimentos.isNotEmpty() || origem != null ||
            valorMinCentavos != null || valorMaxCentavos != null || busca.isNotBlank()

    /** Mantém o período e remove o resto. */
    fun limparRestricoes(): Filtro = Filtro(periodo = periodo)
}
