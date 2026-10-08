package dev.lucasgola.financas.util

import java.math.BigDecimal
import java.math.RoundingMode
import java.text.NumberFormat
import java.util.Locale

val LOCALE_BR: Locale = Locale.forLanguageTag("pt-BR")

/**
 * Converte texto no formato brasileiro ("1.234,56", "10,1", "84,90", "R$ 5") em [BigDecimal].
 * Retorna null para texto vazio ou inválido (ex.: "NaN", que a SEFAZ às vezes exibe).
 */
fun parseDecimalBr(texto: String): BigDecimal? {
    val limpo = texto.replace("R$", "").replace(' ', ' ').trim().replace(" ", "")
    if (limpo.isEmpty()) return null
    val normalizado = limpo.replace(".", "").replace(',', '.')
    if (!Regex("""-?\d+(\.\d+)?""").matches(normalizado)) return null
    return BigDecimal(normalizado)
}

/** Converte reais em centavos. Arredonda meio-para-cima (regra usual em documentos fiscais). */
fun BigDecimal.paraCentavos(): Long = setScale(2, RoundingMode.HALF_UP).movePointRight(2).longValueExact()

fun parseCentavosBr(texto: String): Long? = parseDecimalBr(texto)?.paraCentavos()

fun centavosParaReais(centavos: Long): BigDecimal = BigDecimal.valueOf(centavos, 2)

fun formatarMoeda(centavos: Long): String =
    NumberFormat.getCurrencyInstance(LOCALE_BR).format(centavosParaReais(centavos))

/** Formato para campo de edição, sem símbolo: 1234,56 */
fun formatarDecimalEdicao(centavos: Long): String =
    centavosParaReais(centavos).toPlainString().replace('.', ',')

/** Formata quantidades/valores unitários preservando as casas significativas: 0,432 · 10,1 · 4 */
fun formatarDecimalBr(valor: BigDecimal): String {
    val nf = NumberFormat.getNumberInstance(LOCALE_BR)
    nf.minimumFractionDigits = 0
    nf.maximumFractionDigits = maxOf(valor.stripTrailingZeros().scale(), 0)
    return nf.format(valor)
}

/** 58891504001796 → 58.891.504/0017-96. Retorna o texto original se não tiver 14 dígitos. */
fun formatarCnpj(cnpj: String): String {
    val d = cnpj.filter { it.isDigit() }
    if (d.length != 14) return cnpj
    return "${d.substring(0, 2)}.${d.substring(2, 5)}.${d.substring(5, 8)}/${d.substring(8, 12)}-${d.substring(12)}"
}

/** Rótulo curto para eixos e tabelas: R$ 850 · R$ 1,2 mil · R$ 3,4 mi · −R$ 1,2 mil. */
fun formatarMoedaCompacta(centavos: Long): String {
    val reais = kotlin.math.abs(centavos) / 100.0
    val nf = NumberFormat.getNumberInstance(LOCALE_BR).apply { maximumFractionDigits = 1 }
    val texto = when {
        reais >= 1_000_000 -> "R$ ${nf.format(reais / 1_000_000)} mi"
        reais >= 1_000 -> "R$ ${nf.format(reais / 1_000)} mil"
        else -> "R$ ${nf.format(reais)}"
    }
    return if (centavos < 0) "−$texto" else texto
}
