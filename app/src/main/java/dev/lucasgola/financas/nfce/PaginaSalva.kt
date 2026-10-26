package dev.lucasgola.financas.nfce

import java.io.ByteArrayOutputStream
import java.util.Base64

/**
 * Lê a página da consulta salva pelo navegador ("Baixar página" do Chrome gera um .mhtml) ou um .html comum
 * e devolve o HTML para o [NfceParserSp].
 *
 * MHTML é um MIME multipart/related: o primeiro bloco text/html é a página, em geral em quoted-printable.
 */
object PaginaSalva {

    fun html(bytes: ByteArray): String {
        val texto = String(bytes, Charsets.ISO_8859_1) // 1 byte = 1 char: preserva os bytes até decodificar
        val boundary = Regex("""boundary="?([^";\r\n]+)"?""", RegexOption.IGNORE_CASE)
            .find(cabecalho(texto))?.groupValues?.get(1)
            ?: return String(bytes, Charsets.UTF_8) // não é MHTML: HTML comum

        for (parte in texto.split("--$boundary").drop(1)) {
            val cab = cabecalho(parte.trimStart('\r', '\n'))
            if (!Regex("""content-type:\s*text/html""", RegexOption.IGNORE_CASE).containsMatchIn(cab)) continue
            val corpo = parte.trimStart('\r', '\n').substringAfter(separador(parte.trimStart('\r', '\n')), "")
            val codificacao = Regex("""content-transfer-encoding:\s*(\S+)""", RegexOption.IGNORE_CASE)
                .find(cab)?.groupValues?.get(1)?.lowercase()
            val dados = when (codificacao) {
                "quoted-printable" -> quotedPrintable(corpo)
                "base64" -> Base64.getMimeDecoder().decode(corpo.trim())
                else -> corpo.toByteArray(Charsets.ISO_8859_1)
            }
            return String(dados, Charsets.UTF_8)
        }
        throw NfceParseException("O arquivo não tem a página da nota")
    }

    private fun separador(texto: String) = if (texto.contains("\r\n\r\n")) "\r\n\r\n" else "\n\n"

    private fun cabecalho(texto: String) = texto.substringBefore(separador(texto))

    private fun quotedPrintable(texto: String): ByteArray {
        val saida = ByteArrayOutputStream(texto.length)
        var i = 0
        while (i < texto.length) {
            val c = texto[i]
            if (c == '=') {
                when {
                    texto.startsWith("\r\n", i + 1) -> i += 3 // quebra de linha "mole"
                    texto.startsWith("\n", i + 1) -> i += 2
                    i + 2 < texto.length && texto.substring(i + 1, i + 3).toIntOrNull(16) != null -> {
                        saida.write(texto.substring(i + 1, i + 3).toInt(16))
                        i += 3
                    }
                    else -> { saida.write(c.code); i++ }
                }
            } else {
                saida.write(c.code)
                i++
            }
        }
        return saida.toByteArray()
    }
}
