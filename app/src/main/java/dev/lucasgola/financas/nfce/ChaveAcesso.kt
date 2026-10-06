package dev.lucasgola.financas.nfce

import java.time.YearMonth

/**
 * Chave de acesso de 44 dígitos de NF-e/NFC-e.
 * Layout: cUF(2) AAMM(4) CNPJ(14) mod(2) serie(3) nNF(9) tpEmis(1) cNF(8) cDV(1)
 */
@JvmInline
value class ChaveAcesso private constructor(val valor: String) {

    val uf: String get() = valor.substring(0, 2)
    val anoMes: YearMonth get() = YearMonth.of(2000 + valor.substring(2, 4).toInt(), valor.substring(4, 6).toInt())
    val cnpjEmitente: String get() = valor.substring(6, 20)
    val modelo: String get() = valor.substring(20, 22)
    val serie: Int get() = valor.substring(22, 25).toInt()
    val numero: Long get() = valor.substring(25, 34).toLong()

    override fun toString(): String = valor

    companion object {
        const val UF_SP = "35"

        /** Aceita a chave com ou sem espaços. Retorna null se não tiver 44 dígitos ou o DV não conferir. */
        fun of(texto: String): ChaveAcesso? {
            val digitos = texto.filterNot { it.isWhitespace() }
            if (digitos.length != 44 || !digitos.all { it.isDigit() }) return null
            if (digitoVerificador(digitos.substring(0, 43)) != digitos[43].digitToInt()) return null
            val mes = digitos.substring(4, 6).toInt()
            if (mes !in 1..12) return null
            return ChaveAcesso(digitos)
        }

        /** Módulo 11 com pesos 2..9 da direita para a esquerda (Manual de Orientação do Contribuinte). */
        fun digitoVerificador(base43: String): Int {
            var peso = 2
            var soma = 0
            for (i in base43.indices.reversed()) {
                soma += base43[i].digitToInt() * peso
                peso = if (peso == 9) 2 else peso + 1
            }
            val resto = soma % 11
            return if (resto < 2) 0 else 11 - resto
        }

        /**
         * Extrai a chave do conteúdo de um QR Code de NFC-e.
         * Formatos do parâmetro `p`:
         *  - v2 online:  chave|2|tpAmb|cIdToken|hash
         *  - v2 offline: chave|2|tpAmb|dia|valor|digVal|cIdToken|hash
         *  - v3:         chave|3|tpAmb[|...]
         * O separador pode vir literal ("|") ou codificado ("%7C").
         */
        fun doQrCode(conteudo: String): ChaveAcesso? {
            val decodificado = conteudo.trim().replace("%7C", "|", ignoreCase = true)
            val p = Regex("""[?&]p=([^&#]+)""", RegexOption.IGNORE_CASE).find(decodificado)?.groupValues?.get(1)
                ?: return null
            return of(p.substringBefore('|'))
        }
    }
}
