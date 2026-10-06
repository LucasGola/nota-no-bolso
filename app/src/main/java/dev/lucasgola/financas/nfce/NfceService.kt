package dev.lucasgola.financas.nfce

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.concurrent.TimeUnit

/** Busca a página de consulta da NFC-e na SEFAZ a partir da URL lida no QR Code. */
class NfceService(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build(),
) {
    companion object {
        /**
         * URL de consulta a partir só da chave (QR ilegível, chave digitada).
         * Usa o formato do QR v3 (chave|3|tpAmb), que não exige o hash do QR v2.
         */
        fun urlConsultaSp(chave: ChaveAcesso): String =
            "https://www.nfce.fazenda.sp.gov.br/NFCeConsultaPublica/Paginas/ConsultaQRCode.aspx?p=${chave.valor}%7C3%7C1"
    }

    suspend fun consultar(urlQrCode: String): NotaImportada = withContext(Dispatchers.IO) {
        val chave = ChaveAcesso.doQrCode(urlQrCode) ?: throw NfceParseException("QR Code não é de uma NFC-e válida")
        if (chave.uf != ChaveAcesso.UF_SP) throw NfceParseException("Por enquanto só notas de SP são suportadas")

        val request = Request.Builder()
            .url(urlQrCode.trim())
            .header("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/120 Mobile Safari/537.36")
            .build()
        val html = client.newCall(request).execute().use { resp ->
            if (!resp.isSuccessful) throw IOException("SEFAZ respondeu HTTP ${resp.code}")
            resp.body.string()
        }
        NfceParserSp.parse(html)
    }
}
