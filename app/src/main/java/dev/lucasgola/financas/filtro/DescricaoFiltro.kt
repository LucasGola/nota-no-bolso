package dev.lucasgola.financas.filtro

import dev.lucasgola.financas.data.Categoria
import dev.lucasgola.financas.data.Estabelecimento
import dev.lucasgola.financas.data.OrigemLancamento
import dev.lucasgola.financas.data.TipoLancamento
import dev.lucasgola.financas.util.formatarMoeda
import java.time.LocalDate

/** "Outubro 2026 · Saídas · Categorias: Mercado, Lazer · Busca: "leite"" — para cabeçalhos de relatório. */
fun descreverFiltro(
    filtro: Filtro,
    categorias: List<Categoria>,
    estabelecimentos: List<Estabelecimento>,
    hoje: LocalDate,
): String {
    val partes = mutableListOf<String>()
    val periodo = filtro.periodo
    // No relatório o ano aparece sempre (o rótulo da tela omite o ano corrente).
    partes += if (periodo is Periodo.Mes) "${periodo.rotulo(hoje).substringBefore(' ')} ${periodo.mes.year}" else periodo.rotulo(hoje)
    filtro.tipo?.let { partes += if (it == TipoLancamento.ENTRADA) "Só entradas" else "Só saídas" }
    filtro.origem?.let { partes += if (it == OrigemLancamento.NFCE) "Só notas fiscais" else "Só lançamentos manuais" }
    if (filtro.categorias.isNotEmpty()) {
        partes += "Categorias: " + categorias.filter { it.id in filtro.categorias }.joinToString(", ") { it.nome }
    }
    if (filtro.estabelecimentos.isNotEmpty()) {
        partes += "Estabelecimentos: " + estabelecimentos.filter { it.id in filtro.estabelecimentos }.joinToString(", ") { it.razaoSocial }
    }
    if (filtro.valorMinCentavos != null) partes += "Valor ≥ ${formatarMoeda(filtro.valorMinCentavos)}"
    if (filtro.valorMaxCentavos != null) partes += "Valor ≤ ${formatarMoeda(filtro.valorMaxCentavos)}"
    if (filtro.busca.isNotBlank()) partes += "Busca: \"${filtro.busca.trim()}\""
    return partes.joinToString(" · ")
}
