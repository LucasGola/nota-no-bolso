package dev.lucasgola.financas.ui.comum

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import dev.lucasgola.financas.data.Categoria
import dev.lucasgola.financas.util.formatar
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SeletorCategoria(
    categorias: List<Categoria>,
    selecionada: Long?,
    onSelecionar: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    var aberto by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = aberto, onExpandedChange = { aberto = it }, modifier = modifier) {
        OutlinedTextField(
            value = categorias.firstOrNull { it.id == selecionada }?.nome.orEmpty(),
            onValueChange = {},
            readOnly = true,
            label = { Text("Categoria") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = aberto) },
            modifier = Modifier.fillMaxWidth().menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
        )
        ExposedDropdownMenu(expanded = aberto, onDismissRequest = { aberto = false }) {
            categorias.forEach { c ->
                DropdownMenuItem(text = { Text(c.nome) }, onClick = { onSelecionar(c.id); aberto = false })
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CampoData(data: LocalDate, onAlterar: (LocalDate) -> Unit, modifier: Modifier = Modifier) {
    var escolhendo by remember { mutableStateOf(false) }
    OutlinedTextField(
        value = data.formatar(),
        onValueChange = {},
        readOnly = true,
        label = { Text("Data") },
        trailingIcon = {
            IconButton(onClick = { escolhendo = true }) { Icon(Icons.Default.DateRange, "Escolher data") }
        },
        modifier = modifier.fillMaxWidth(),
    )
    if (escolhendo) {
        val estado = rememberDatePickerState(
            initialSelectedDateMillis = data.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
        )
        DatePickerDialog(
            onDismissRequest = { escolhendo = false },
            confirmButton = {
                TextButton(onClick = {
                    // O DatePicker trabalha em UTC: converter com UTC evita cair no dia anterior.
                    estado.selectedDateMillis?.let { ms ->
                        onAlterar(Instant.ofEpochMilli(ms).atZone(ZoneOffset.UTC).toLocalDate())
                    }
                    escolhendo = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { escolhendo = false }) { Text("Cancelar") } },
        ) { DatePicker(estado) }
    }
}

@Composable
fun CampoValor(texto: String, onAlterar: (String) -> Unit, modifier: Modifier = Modifier) {
    OutlinedTextField(
        value = texto,
        onValueChange = { v -> onAlterar(v.filter { c -> c.isDigit() || c == ',' || c == '.' }) },
        label = { Text("Valor (R$)") },
        placeholder = { Text("0,00") },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        singleLine = true,
        modifier = modifier.fillMaxWidth(),
    )
}
