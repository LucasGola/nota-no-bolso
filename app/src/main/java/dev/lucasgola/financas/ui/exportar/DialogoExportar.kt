package dev.lucasgola.financas.ui.exportar

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import dev.lucasgola.financas.export.Exportador
import dev.lucasgola.financas.export.FormatoExportacao
import dev.lucasgola.financas.filtro.Filtro
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import java.io.File

/** Exporta exatamente o que o filtro atual mostra, em CSV ou PDF, para compartilhar ou salvar. */
@Composable
fun DialogoExportar(exportador: Exportador, filtro: Filtro, descricaoPeriodo: String, onFechar: () -> Unit) {
    val context = LocalContext.current
    val escopo = rememberCoroutineScope()
    var formato by remember { mutableStateOf(FormatoExportacao.PDF) }
    var incluirItens by remember { mutableStateOf(false) }
    var gerando by remember { mutableStateOf(false) }
    var erro by remember { mutableStateOf<String?>(null) }
    // Arquivo já gerado, aguardando o usuário escolher onde salvar.
    var pendente by remember { mutableStateOf<File?>(null) }

    fun aoSalvar(destino: android.net.Uri?) {
        val arquivo = pendente ?: return
        if (destino == null) { gerando = false; return } // usuário cancelou o seletor
        escopo.launch {
            try {
                exportador.copiarPara(arquivo, destino)
                Toast.makeText(context, "Arquivo salvo", Toast.LENGTH_SHORT).show()
                onFechar()
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                erro = "Não foi possível salvar: ${e.message}"
                gerando = false
            }
        }
    }
    // O contrato fixa o tipo do arquivo na criação, então há um seletor por formato.
    val salvarCsv = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(FormatoExportacao.CSV.mime), ::aoSalvar)
    val salvarPdf = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(FormatoExportacao.PDF.mime), ::aoSalvar)

    fun gerarE(acao: (File) -> Unit) {
        gerando = true
        erro = null
        escopo.launch {
            try {
                acao(exportador.gerar(filtro, formato, incluirItens))
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                erro = "Não foi possível gerar o arquivo: ${e.message}"
                gerando = false
            }
        }
    }

    AlertDialog(
        onDismissRequest = { if (!gerando) onFechar() },
        title = { Text("Exportar") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "Exporta os lançamentos do filtro atual: $descricaoPeriodo.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OpcaoFormato("PDF (relatório para ler ou imprimir)", formato == FormatoExportacao.PDF) { formato = FormatoExportacao.PDF }
                OpcaoFormato("CSV (planilha: Excel, Google Sheets)", formato == FormatoExportacao.CSV) { formato = FormatoExportacao.CSV }
                Row(
                    Modifier.fillMaxWidth().toggleable(incluirItens, role = Role.Checkbox) { incluirItens = it },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(incluirItens, onCheckedChange = null)
                    Text("Incluir os itens das notas fiscais")
                }
                if (gerando) LinearProgressIndicator(Modifier.fillMaxWidth())
                erro?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                // Ações principais com largura total: três botões de texto não cabem numa linha.
                Button(
                    enabled = !gerando,
                    onClick = {
                        gerarE { arquivo ->
                            context.startActivity(exportador.intentCompartilhar(arquivo, formato))
                            onFechar()
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Compartilhar") }
                OutlinedButton(
                    enabled = !gerando,
                    onClick = {
                        gerarE { arquivo ->
                            pendente = arquivo
                            if (formato == FormatoExportacao.CSV) salvarCsv.launch(arquivo.name) else salvarPdf.launch(arquivo.name)
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Salvar no aparelho…") }
            }
        },
        confirmButton = {
            TextButton(enabled = !gerando, onClick = onFechar) { Text("Cancelar") }
        },
    )
}

@Composable
private fun OpcaoFormato(rotulo: String, selecionado: Boolean, onSelecionar: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().selectable(selecionado, role = Role.RadioButton, onClick = onSelecionar),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selecionado, onClick = null)
        Text(rotulo)
    }
}
