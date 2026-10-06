package dev.lucasgola.financas.ui.filtro

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AssistChip
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDateRangePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import dev.lucasgola.financas.R
import dev.lucasgola.financas.data.Categoria
import dev.lucasgola.financas.data.Estabelecimento
import dev.lucasgola.financas.data.OrigemLancamento
import dev.lucasgola.financas.data.TipoLancamento
import dev.lucasgola.financas.filtro.Filtro
import dev.lucasgola.financas.filtro.Periodo
import dev.lucasgola.financas.util.ZONA
import dev.lucasgola.financas.util.formatarDecimalEdicao
import dev.lucasgola.financas.util.formatarMoeda
import dev.lucasgola.financas.util.parseCentavosBr
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/**
 * Período (com setas para navegar entre meses) + chips das restrições ativas.
 * Cada chip remove a sua restrição com um toque; "Limpar" remove todas.
 */
@Composable
fun BarraFiltros(
    filtro: Filtro,
    categorias: List<Categoria>,
    estabelecimentos: List<Estabelecimento>,
    onAlterar: ((Filtro) -> Filtro) -> Unit,
    modifier: Modifier = Modifier,
) {
    val hoje = LocalDate.now(ZONA)
    var editando by remember { mutableStateOf(false) }

    Column(modifier) {
        SeletorPeriodo(filtro.periodo, hoje) { p -> onAlterar { it.copy(periodo = p) } }

        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                FilterChip(
                    selected = filtro.temRestricoes,
                    onClick = { editando = true },
                    label = { Text("Filtros") },
                    leadingIcon = { Icon(painterResource(R.drawable.ic_filtro), null, Modifier.size(18.dp)) },
                )
            }
            filtro.tipo?.let { t ->
                item { ChipRemovivel(if (t == TipoLancamento.ENTRADA) "Entradas" else "Saídas") { onAlterar { it.copy(tipo = null) } } }
            }
            filtro.origem?.let { o ->
                item { ChipRemovivel(if (o == OrigemLancamento.NFCE) "Nota fiscal" else "Manual") { onAlterar { it.copy(origem = null) } } }
            }
            categorias.filter { it.id in filtro.categorias }.forEach { c ->
                item(key = "cat-${c.id}") { ChipRemovivel(c.nome) { onAlterar { it.copy(categorias = it.categorias - c.id) } } }
            }
            estabelecimentos.filter { it.id in filtro.estabelecimentos }.forEach { e ->
                item(key = "est-${e.id}") {
                    ChipRemovivel(e.razaoSocial) { onAlterar { it.copy(estabelecimentos = it.estabelecimentos - e.id) } }
                }
            }
            if (filtro.valorMinCentavos != null || filtro.valorMaxCentavos != null) item {
                ChipRemovivel(rotuloValor(filtro)) { onAlterar { it.copy(valorMinCentavos = null, valorMaxCentavos = null) } }
            }
            if (filtro.busca.isNotBlank()) item {
                ChipRemovivel("\"${filtro.busca.trim()}\"") { onAlterar { it.copy(busca = "") } }
            }
            if (filtro.temRestricoes) item {
                AssistChip(onClick = { onAlterar { it.limparRestricoes() } }, label = { Text("Limpar") })
            }
        }
    }

    if (editando) {
        PainelFiltros(filtro, categorias, estabelecimentos, onAlterar, onFechar = { editando = false })
    }
}

private fun rotuloValor(f: Filtro): String = when {
    f.valorMinCentavos != null && f.valorMaxCentavos != null ->
        "${formatarMoeda(f.valorMinCentavos)} a ${formatarMoeda(f.valorMaxCentavos)}"
    f.valorMinCentavos != null -> "≥ ${formatarMoeda(f.valorMinCentavos)}"
    else -> "≤ ${formatarMoeda(f.valorMaxCentavos ?: 0)}"
}

@Composable
private fun ChipRemovivel(texto: String, onRemover: () -> Unit) {
    InputChip(
        selected = true,
        onClick = onRemover,
        label = { Text(texto, maxLines = 1) },
        trailingIcon = { Icon(Icons.Default.Close, "Remover filtro", Modifier.size(18.dp)) },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SeletorPeriodo(periodo: Periodo, hoje: LocalDate, onAlterar: (Periodo) -> Unit) {
    var menu by remember { mutableStateOf(false) }
    var personalizando by remember { mutableStateOf(false) }

    Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        val mes = periodo as? Periodo.Mes
        IconButton(onClick = { mes?.let { onAlterar(Periodo.Mes(it.mes.minusMonths(1))) } }, enabled = mes != null) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "Mês anterior")
        }
        Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
            TextButton(onClick = { menu = true }) {
                Text(periodo.rotulo(hoje), style = MaterialTheme.typography.titleMedium)
                Icon(Icons.Default.ArrowDropDown, null)
            }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                listOf(
                    "Este mês" to Periodo.mesAtual(hoje),
                    "Mês anterior" to Periodo.Mes(Periodo.mesAtual(hoje).mes.minusMonths(1)),
                    "Últimos 30 dias" to Periodo.UltimosDias(30),
                    "Últimos 90 dias" to Periodo.UltimosDias(90),
                    "Este ano" to Periodo.Ano(hoje.year),
                    "Todo o período" to Periodo.Tudo,
                ).forEach { (rotulo, p) ->
                    DropdownMenuItem(text = { Text(rotulo) }, onClick = { menu = false; onAlterar(p) })
                }
                DropdownMenuItem(text = { Text("Personalizado…") }, onClick = { menu = false; personalizando = true })
            }
        }
        IconButton(onClick = { mes?.let { onAlterar(Periodo.Mes(it.mes.plusMonths(1))) } }, enabled = mes != null) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, "Próximo mês")
        }
    }

    if (personalizando) {
        val intervalo = periodo.intervalo(hoje)
        val estado = rememberDateRangePickerState(
            initialSelectedStartDateMillis = intervalo?.inicio?.paraMillisUtc(),
            initialSelectedEndDateMillis = intervalo?.fim?.paraMillisUtc(),
        )
        DatePickerDialog(
            onDismissRequest = { personalizando = false },
            confirmButton = {
                TextButton(
                    enabled = estado.selectedStartDateMillis != null && estado.selectedEndDateMillis != null,
                    onClick = {
                        onAlterar(Periodo.Personalizado(estado.selectedStartDateMillis!!.paraData(), estado.selectedEndDateMillis!!.paraData()))
                        personalizando = false
                    },
                ) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { personalizando = false }) { Text("Cancelar") } },
        ) {
            DateRangePicker(estado, Modifier.height(500.dp))
        }
    }
}

// O DatePicker trabalha em UTC: converter com UTC evita cair no dia anterior.
private fun LocalDate.paraMillisUtc(): Long = atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
private fun Long.paraData(): LocalDate = Instant.ofEpochMilli(this).atZone(ZoneOffset.UTC).toLocalDate()

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun PainelFiltros(
    filtro: Filtro,
    categorias: List<Categoria>,
    estabelecimentos: List<Estabelecimento>,
    onAlterar: ((Filtro) -> Filtro) -> Unit,
    onFechar: () -> Unit,
) {
    // Valores digitados ficam locais até serem válidos, para não filtrar a cada tecla com texto incompleto.
    var minTexto by remember { mutableStateOf(filtro.valorMinCentavos?.let(::formatarDecimalEdicao).orEmpty()) }
    var maxTexto by remember { mutableStateOf(filtro.valorMaxCentavos?.let(::formatarDecimalEdicao).orEmpty()) }

    ModalBottomSheet(onDismissRequest = onFechar, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            Modifier.padding(horizontal = 16.dp).padding(bottom = 32.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            OutlinedTextField(
                value = filtro.busca,
                onValueChange = { v -> onAlterar { it.copy(busca = v) } },
                label = { Text("Buscar na descrição e nos itens") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            Secao("Tipo") {
                Segmentos(
                    opcoes = listOf(null to "Todos", TipoLancamento.SAIDA to "Saídas", TipoLancamento.ENTRADA to "Entradas"),
                    selecionado = filtro.tipo,
                    onSelecionar = { t -> onAlterar { it.copy(tipo = t) } },
                )
            }

            Secao("Origem") {
                Segmentos(
                    opcoes = listOf(null to "Todas", OrigemLancamento.NFCE to "Nota fiscal", OrigemLancamento.MANUAL to "Manual"),
                    selecionado = filtro.origem,
                    onSelecionar = { o -> onAlterar { it.copy(origem = o) } },
                )
            }

            Secao("Categorias") {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    categorias.forEach { c ->
                        val sel = c.id in filtro.categorias
                        FilterChip(
                            selected = sel,
                            onClick = { onAlterar { it.copy(categorias = if (sel) it.categorias - c.id else it.categorias + c.id) } },
                            label = { Text(c.nome) },
                        )
                    }
                }
            }

            if (estabelecimentos.isNotEmpty()) Secao("Estabelecimentos") {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    estabelecimentos.forEach { e ->
                        val sel = e.id in filtro.estabelecimentos
                        FilterChip(
                            selected = sel,
                            onClick = {
                                onAlterar { it.copy(estabelecimentos = if (sel) it.estabelecimentos - e.id else it.estabelecimentos + e.id) }
                            },
                            label = { Text(e.razaoSocial, maxLines = 1) },
                        )
                    }
                }
            }

            Secao("Valor") {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = minTexto,
                        onValueChange = { v ->
                            minTexto = v.filter { it.isDigit() || it == ',' || it == '.' }
                            onAlterar { it.copy(valorMinCentavos = parseCentavosBr(minTexto)) }
                        },
                        label = { Text("Mínimo") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                    OutlinedTextField(
                        value = maxTexto,
                        onValueChange = { v ->
                            maxTexto = v.filter { it.isDigit() || it == ',' || it == '.' }
                            onAlterar { it.copy(valorMaxCentavos = parseCentavosBr(maxTexto)) }
                        },
                        label = { Text("Máximo") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = {
                    minTexto = ""
                    maxTexto = ""
                    onAlterar { it.limparRestricoes() }
                }) { Text("Limpar") }
                TextButton(onClick = onFechar) { Text("Ver resultados") }
            }
        }
    }
}

@Composable
private fun Secao(titulo: String, conteudo: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(titulo, style = MaterialTheme.typography.titleSmall)
        conteudo()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun <T> Segmentos(opcoes: List<Pair<T?, String>>, selecionado: T?, onSelecionar: (T?) -> Unit) {
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
        opcoes.forEachIndexed { i, (valor, rotulo) ->
            SegmentedButton(
                selected = selecionado == valor,
                onClick = { onSelecionar(valor) },
                shape = SegmentedButtonDefaults.itemShape(i, opcoes.size),
            ) { Text(rotulo) }
        }
    }
}
