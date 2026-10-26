package dev.lucasgola.financas.nfce

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.util.Base64

class PaginaSalvaTest {

    private val html = javaClass.getResource("/nfce/sp_restaurante_3itens.html")!!.readText(Charsets.UTF_8)

    /** Quoted-printable como o Chrome grava: linhas de até 76 caracteres com "=" de quebra mole. */
    private fun quotedPrintable(texto: String): String {
        val sb = StringBuilder()
        var linha = 0
        for (b in texto.toByteArray(Charsets.UTF_8)) {
            val v = b.toInt() and 0xFF
            val pedaco = if (v in 33..126 && v != '='.code || v == ' '.code) v.toChar().toString() else "=%02X".format(v)
            if (linha + pedaco.length > 75) { sb.append("=\r\n"); linha = 0 }
            sb.append(pedaco); linha += pedaco.length
        }
        return sb.toString()
    }

    /** Estrutura de um .mhtml salvo pelo "Baixar página" do Chrome, com uma imagem depois da página. */
    private fun mhtml(corpoHtml: String, codificacao: String) = (
        "From: <Saved by Blink>\r\n" +
            "Snapshot-Content-Location: https://www.nfce.fazenda.sp.gov.br/NFCeConsultaPublica/Paginas/ConsultaQRCode.aspx\r\n" +
            "Subject: =?utf-8?Q?Consulta?=\r\n" +
            "MIME-Version: 1.0\r\n" +
            "Content-Type: multipart/related;\r\n" +
            "\ttype=\"text/html\";\r\n" +
            "\tboundary=\"----MultipartBoundary--abc123----\"\r\n" +
            "\r\n\r\n" +
            "------MultipartBoundary--abc123----\r\n" +
            "Content-Type: text/html\r\n" +
            "Content-ID: <frame-1@mhtml.blink>\r\n" +
            "Content-Transfer-Encoding: $codificacao\r\n" +
            "Content-Location: https://www.nfce.fazenda.sp.gov.br/\r\n" +
            "\r\n" +
            corpoHtml + "\r\n" +
            "------MultipartBoundary--abc123----\r\n" +
            "Content-Type: image/png\r\n" +
            "Content-Transfer-Encoding: base64\r\n" +
            "\r\n" +
            "iVBORw0KGgo=\r\n" +
            "------MultipartBoundary--abc123------\r\n"
        ).toByteArray(Charsets.ISO_8859_1)

    @Test
    fun `mhtml do chrome em quoted-printable vira a nota com acentos`() {
        val nota = NfceParserSp.parse(PaginaSalva.html(mhtml(quotedPrintable(html), "quoted-printable")))
        assertEquals("35261058891504001796650020000075601259531450", nota.chave.valor)
        assertEquals("Batata Média", nota.itens[1].descricao)
        assertEquals(8490L, nota.valorAPagarCentavos)
    }

    @Test
    fun `mhtml em base64`() {
        val corpo = Base64.getMimeEncoder().encodeToString(html.toByteArray(Charsets.UTF_8))
        assertEquals(3, NfceParserSp.parse(PaginaSalva.html(mhtml(corpo, "base64"))).itens.size)
    }

    @Test
    fun `html comum passa direto`() {
        assertEquals(3, NfceParserSp.parse(PaginaSalva.html(html.toByteArray(Charsets.UTF_8))).itens.size)
    }

    @Test
    fun `mhtml sem pagina html da erro`() {
        val soImagem = ("MIME-Version: 1.0\r\nContent-Type: multipart/related; boundary=\"b\"\r\n\r\n" +
            "--b\r\nContent-Type: image/png\r\n\r\nxx\r\n--b--\r\n").toByteArray()
        assertThrows(NfceParseException::class.java) { PaginaSalva.html(soImagem) }
    }
}
