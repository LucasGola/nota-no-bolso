package dev.lucasgola.financas.ui.comum

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.lucasgola.financas.data.Categoria
import dev.lucasgola.financas.data.TipoCategoria

/**
 * Escolha da categoria própria de um item de nota: "Mesma da nota" (null), as categorias só de itens e as de gasto.
 * Usado na revisão da importação e no lançamento já salvo.
 */
@Composable
fun DialogoCategoriaItem(
    item: LinhaItem,
    categorias: List<Categoria>,
    categoriaDaNota: String?,
    onEscolher: (Long?) -> Unit,
    onCancelar: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onCancelar,
        title = { Text(item.descricao, maxLines = 2, overflow = TextOverflow.Ellipsis) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                OpcaoCategoria(
                    "Mesma da nota" + (categoriaDaNota?.let { " ($it)" } ?: ""),
                    selecionada = item.categoriaId == null,
                ) { onEscolher(null) }
                // Primeiro as categorias feitas para itens; depois as de gasto, que também podem ser usadas.
                val (deItem, deGasto) = categorias.partition { it.tipo == TipoCategoria.ITEM }
                if (deItem.isNotEmpty()) TituloGrupo("Categorias de itens")
                deItem.forEach { c -> OpcaoCategoria(c.nome, selecionada = item.categoriaId == c.id) { onEscolher(c.id) } }
                if (deGasto.isNotEmpty()) TituloGrupo("Categorias de gasto")
                deGasto.forEach { c -> OpcaoCategoria(c.nome, selecionada = item.categoriaId == c.id) { onEscolher(c.id) } }
                if (deItem.isEmpty()) Text(
                    "Dica: em Cadastros › Categorias, crie categorias do tipo \"Só itens de nota\" (ex.: Carne, Limpeza). " +
                        "Elas aparecem aqui e não poluem a lista de categorias dos lançamentos.",
                    Modifier.padding(top = 8.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = { TextButton(onClick = onCancelar) { Text("Cancelar") } },
    )
}

@Composable
private fun TituloGrupo(texto: String) = Text(
    texto,
    Modifier.padding(top = 12.dp, bottom = 4.dp),
    style = MaterialTheme.typography.labelLarge,
    color = MaterialTheme.colorScheme.primary,
)

@Composable
private fun OpcaoCategoria(nome: String, selecionada: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().selectable(selecionada, role = Role.RadioButton, onClick = onClick).padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selecionada, onClick = null)
        Text(nome, Modifier.padding(start = 8.dp))
    }
}
