package dev.lucasgola.financas.ui.importar

import android.content.Context
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.input.KeyboardType
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning
import dev.lucasgola.financas.nfce.ChaveAcesso
import dev.lucasgola.financas.nfce.NfceService

/**
 * Abre o leitor de QR do Google Play services (UI pronta, sem permissão de câmera no app).
 * Cancelamento pelo usuário não chama nenhum callback.
 */
fun lerQrCode(context: Context, onLido: (String) -> Unit, onErro: (String) -> Unit) {
    val opcoes = GmsBarcodeScannerOptions.Builder()
        .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
        .enableAutoZoom()
        .build()
    GmsBarcodeScanning.getClient(context, opcoes)
        .startScan()
        .addOnSuccessListener { codigo ->
            val valor = codigo.rawValue
            if (valor.isNullOrBlank()) onErro("QR Code vazio.") else onLido(valor)
        }
        .addOnFailureListener { e -> onErro("Leitor indisponível: ${e.message}") }
}

/** Chave digitada (QR ilegível): valida o DV e devolve a URL de consulta equivalente. */
@Composable
fun DialogoDigitarChave(onConfirmar: (urlConsulta: String) -> Unit, onCancelar: () -> Unit) {
    var texto by remember { mutableStateOf("") }
    val digitos = texto.filter { it.isDigit() }
    val chave = ChaveAcesso.of(digitos)
    AlertDialog(
        onDismissRequest = onCancelar,
        title = { Text("Digitar chave de acesso") },
        text = {
            Column {
                OutlinedTextField(
                    value = texto,
                    onValueChange = { v -> texto = v.filter { it.isDigit() || it == ' ' }.take(60) },
                    label = { Text("44 dígitos") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    supportingText = {
                        when {
                            digitos.length < 44 -> Text("${digitos.length}/44")
                            chave == null -> Text("Chave inválida (confira os dígitos)", color = MaterialTheme.colorScheme.error)
                            else -> Text("Chave válida")
                        }
                    },
                )
            }
        },
        confirmButton = {
            TextButton(enabled = chave != null, onClick = { chave?.let { onConfirmar(NfceService.urlConsultaSp(it)) } }) {
                Text("Consultar")
            }
        },
        dismissButton = { TextButton(onClick = onCancelar) { Text("Cancelar") } },
    )
}
