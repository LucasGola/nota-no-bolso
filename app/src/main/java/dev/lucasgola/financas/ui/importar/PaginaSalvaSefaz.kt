package dev.lucasgola.financas.ui.importar

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

/**
 * Segunda tentativa quando a SEFAZ recusa a consulta feita pelo app: o usuário abre a nota no navegador
 * (que a SEFAZ aceita), salva a página e escolhe o arquivo aqui.
 */
@Composable
fun PaginaSalvaSefaz(url: String, erro: String?, onArquivo: (Uri) -> Unit, onDeixarPendente: () -> Unit) {
    val context = LocalContext.current
    val escolher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { it?.let(onArquivo) }

    Column(
        Modifier.fillMaxSize().padding(24.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("A SEFAZ recusou a consulta feita pelo app", style = MaterialTheme.typography.titleMedium)
        Text(
            "A nota já está guardada como pendente. Para trazer os itens, abra a nota no navegador e salve a página:\n\n" +
                "1. Toque em \"Abrir no navegador\".\n" +
                "2. Quando a nota aparecer, toque em ⋮ e no ícone de download (Baixar página).\n" +
                "3. Volte ao app e toque em \"Escolher página salva\". O arquivo fica em Downloads.",
            style = MaterialTheme.typography.bodyMedium,
        )
        if (erro != null) {
            Text(erro, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
        }
        Button(
            onClick = {
                try {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                } catch (_: ActivityNotFoundException) {
                    // Sem navegador instalado: não há o que fazer aqui; a nota segue pendente.
                }
            },
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Abrir no navegador") }
        OutlinedButton(onClick = { escolher.launch(arrayOf("*/*")) }, modifier = Modifier.fillMaxWidth()) {
            Text("Escolher página salva")
        }
        TextButton(onClick = onDeixarPendente, modifier = Modifier.fillMaxWidth()) { Text("Deixar pendente") }
    }
}
