package dev.lucasgola.financas.ui.comum

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.lucasgola.financas.util.formatarDecimalBr
import dev.lucasgola.financas.util.formatarMoeda
import java.math.BigDecimal

/** Linha de item, independente da origem (parser ou banco). */
data class LinhaItem(
    val descricao: String,
    val codigo: String,
    val quantidade: BigDecimal,
    val unidade: String,
    val valorUnitario: BigDecimal,
    val valorTotalCentavos: Long,
)

/** Lista de itens no formato do cupom: descrição, depois "2 UN × R$ 9,35" e o total à direita. */
@Composable
fun ListaItensNota(itens: List<LinhaItem>, modifier: Modifier = Modifier) {
    Column(modifier) {
        itens.forEachIndexed { i, item ->
            if (i > 0) HorizontalDivider()
            Row(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                Column(Modifier.weight(1f)) {
                    Text(item.descricao, style = MaterialTheme.typography.bodyMedium)
                    Text(
                        "${formatarDecimalBr(item.quantidade)} ${item.unidade} × R$ ${formatarDecimalBr(item.valorUnitario)}" +
                            if (item.codigo.isNotBlank()) "  ·  cód. ${item.codigo}" else "",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.width(8.dp))
                Text(formatarMoeda(item.valorTotalCentavos), style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
fun LinhaTotal(rotulo: String, centavos: Long, destaque: Boolean = false) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        val estilo = if (destaque) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyMedium
        val peso = if (destaque) FontWeight.SemiBold else FontWeight.Normal
        Text(rotulo, style = estilo, fontWeight = peso)
        Text(formatarMoeda(centavos), style = estilo, fontWeight = peso)
    }
}
