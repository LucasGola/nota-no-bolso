package dev.lucasgola.financas.ui.importar

import android.annotation.SuppressLint
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import dev.lucasgola.financas.nfce.NfceParserSp
import kotlinx.coroutines.delay
import org.json.JSONTokener

/**
 * Segunda tentativa de consulta: abre a página da SEFAZ num navegador de verdade, à vista do usuário
 * (como abrir o link do QR no Chrome). Se a SEFAZ pedir alguma verificação, ela roda aqui ou o usuário
 * resolve na tela. Assim que a página tem o resultado da consulta, o HTML é entregue a [onPagina].
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun NavegadorSefaz(url: String, onPagina: (String) -> Unit, onDesistir: () -> Unit) {
    val entregar by rememberUpdatedState(onPagina)
    var webView by remember { mutableStateOf<WebView?>(null) }
    var entregue by remember { mutableStateOf(false) }

    fun conferir(w: WebView) {
        if (entregue) return
        w.evaluateJavascript("document.documentElement.outerHTML") { json ->
            val html = (JSONTokener(json).nextValue() as? String) ?: return@evaluateJavascript
            if (!entregue && NfceParserSp.temResultado(html)) {
                entregue = true
                entregar(html)
            }
        }
    }

    // Além do fim de cada carregamento, confere de tempos em tempos: a verificação da SEFAZ pode trocar a página por script.
    LaunchedEffect(webView) {
        val w = webView ?: return@LaunchedEffect
        while (!entregue) {
            delay(1_500)
            conferir(w)
        }
    }

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CircularProgressIndicator(Modifier.size(24.dp))
            Text(
                "A consulta direta falhou. Tentando pelo site da SEFAZ…",
                Modifier.weight(1f),
                style = MaterialTheme.typography.bodyMedium,
            )
            OutlinedButton(onClick = onDesistir) { Text("Desistir") }
        }
        AndroidView(
            factory = { ctx ->
                WebView(ctx).apply {
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    webViewClient = object : WebViewClient() {
                        override fun onPageFinished(view: WebView, url: String?) = conferir(view)
                    }
                    loadUrl(url)
                    webView = this
                }
            },
            onRelease = { it.destroy() },
            modifier = Modifier.fillMaxWidth().weight(1f),
        )
    }
}
