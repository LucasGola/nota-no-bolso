package dev.lucasgola.financas.ui.lancamento

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.lucasgola.financas.data.AppDatabase
import dev.lucasgola.financas.data.Categoria
import dev.lucasgola.financas.data.CategoriaDao
import dev.lucasgola.financas.data.Lancamento
import dev.lucasgola.financas.data.LancamentoDao
import dev.lucasgola.financas.data.OrigemLancamento
import dev.lucasgola.financas.data.TipoLancamento
import dev.lucasgola.financas.util.ZONA
import dev.lucasgola.financas.util.dataLocal
import dev.lucasgola.financas.util.formatar
import dev.lucasgola.financas.util.formatarDecimalEdicao
import dev.lucasgola.financas.util.meioDia
import dev.lucasgola.financas.util.parseCentavosBr
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

data class FormLancamento(
    val tipo: TipoLancamento = TipoLancamento.SAIDA,
    val valorTexto: String = "",
    val data: LocalDate = LocalDate.now(ZONA),
    val categoriaId: Long? = null,
    val descricao: String = "",
    val formaPagamento: String = "",
    val observacao: String = "",
    val erro: String? = null,
)

class LancamentoFormViewModel(
    private val lancamentoDao: LancamentoDao,
    categoriaDao: CategoriaDao,
    private val id: Long?,
) : ViewModel() {

    private val _form = MutableStateFlow(FormLancamento())
    val form: StateFlow<FormLancamento> = _form.asStateFlow()

    val categorias: StateFlow<List<Categoria>> = categoriaDao.observarTodas()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Lançamento original em edição; preserva campos que o formulário não mostra (hora, origem, estabelecimento). */
    private var original: Lancamento? = null

    val editando: Boolean get() = id != null

    init {
        if (id != null) viewModelScope.launch {
            lancamentoDao.buscar(id)?.let { l ->
                original = l
                _form.value = FormLancamento(
                    tipo = l.tipo,
                    valorTexto = formatarDecimalEdicao(l.valorCentavos),
                    data = l.dataHora.dataLocal(),
                    categoriaId = l.categoriaId,
                    descricao = l.descricao,
                    formaPagamento = l.formaPagamento.orEmpty(),
                    observacao = l.observacao.orEmpty(),
                )
            }
        }
    }

    fun alterar(f: (FormLancamento) -> FormLancamento) = _form.update { f(it).copy(erro = null) }

    fun salvar(onOk: () -> Unit) {
        val f = _form.value
        val valor = parseCentavosBr(f.valorTexto)
        val erro = when {
            valor == null || valor <= 0 -> "Informe um valor maior que zero"
            f.categoriaId == null -> "Escolha uma categoria"
            else -> null
        }
        if (erro != null) {
            _form.update { it.copy(erro = erro) }
            return
        }
        viewModelScope.launch {
            val o = original
            // Mantém a hora original (ex.: hora da nota fiscal) se a data não mudou.
            val dataHora = if (o != null && o.dataHora.dataLocal() == f.data) o.dataHora else f.data.meioDia()
            val novo = Lancamento(
                id = o?.id ?: 0,
                tipo = f.tipo,
                valorCentavos = valor!!,
                dataHora = dataHora,
                descricao = f.descricao.trim(),
                categoriaId = f.categoriaId!!,
                formaPagamento = f.formaPagamento.trim().ifEmpty { null },
                observacao = f.observacao.trim().ifEmpty { null },
                origem = o?.origem ?: OrigemLancamento.MANUAL,
                estabelecimentoId = o?.estabelecimentoId,
                criadoEm = o?.criadoEm ?: Instant.now(),
                atualizadoEm = Instant.now(),
            )
            if (o == null) lancamentoDao.inserir(novo) else lancamentoDao.atualizar(novo)
            onOk()
        }
    }

    fun excluir(onOk: () -> Unit) {
        val o = original ?: return
        viewModelScope.launch {
            lancamentoDao.excluir(o)
            onOk()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LancamentoFormScreen(db: AppDatabase, lancamentoId: Long?, onFechar: () -> Unit) {
    val vm: LancamentoFormViewModel = viewModel(key = "lancamento-$lancamentoId") {
        LancamentoFormViewModel(db.lancamentoDao(), db.categoriaDao(), lancamentoId)
    }
    val form by vm.form.collectAsStateWithLifecycle()
    val categorias by vm.categorias.collectAsStateWithLifecycle()

    var confirmarExclusao by remember { mutableStateOf(false) }
    var escolhendoData by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (vm.editando) "Editar lançamento" else "Novo lançamento") },
                navigationIcon = {
                    IconButton(onClick = onFechar) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Voltar") }
                },
                actions = {
                    if (vm.editando) IconButton(onClick = { confirmarExclusao = true }) {
                        Icon(Icons.Default.Delete, "Excluir")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).padding(16.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                TipoLancamento.entries.forEachIndexed { i, tipo ->
                    SegmentedButton(
                        selected = form.tipo == tipo,
                        onClick = {
                            vm.alterar { f ->
                                val cat = categorias.firstOrNull { it.id == f.categoriaId }
                                // Troca de tipo descarta categoria incompatível.
                                f.copy(tipo = tipo, categoriaId = f.categoriaId.takeIf { cat?.tipo?.aceita(tipo) == true })
                            }
                        },
                        shape = SegmentedButtonDefaults.itemShape(i, TipoLancamento.entries.size),
                    ) { Text(if (tipo == TipoLancamento.ENTRADA) "Entrada" else "Saída") }
                }
            }

            OutlinedTextField(
                value = form.valorTexto,
                onValueChange = { v -> vm.alterar { it.copy(valorTexto = v.filter { c -> c.isDigit() || c == ',' || c == '.' }) } },
                label = { Text("Valor (R$)") },
                placeholder = { Text("0,00") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            OutlinedTextField(
                value = form.data.formatar(),
                onValueChange = {},
                readOnly = true,
                label = { Text("Data") },
                trailingIcon = {
                    IconButton(onClick = { escolhendoData = true }) { Icon(Icons.Default.DateRange, "Escolher data") }
                },
                modifier = Modifier.fillMaxWidth(),
            )

            SeletorCategoria(
                categorias = categorias.filter { (it.ativa && it.tipo.aceita(form.tipo)) || it.id == form.categoriaId },
                selecionada = form.categoriaId,
                onSelecionar = { id -> vm.alterar { it.copy(categoriaId = id) } },
            )

            OutlinedTextField(
                value = form.descricao,
                onValueChange = { v -> vm.alterar { it.copy(descricao = v) } },
                label = { Text("Descrição") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = form.formaPagamento,
                onValueChange = { v -> vm.alterar { it.copy(formaPagamento = v) } },
                label = { Text("Forma de pagamento") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = form.observacao,
                onValueChange = { v -> vm.alterar { it.copy(observacao = v) } },
                label = { Text("Observação") },
                minLines = 2,
                modifier = Modifier.fillMaxWidth(),
            )

            form.erro?.let { Text(it, color = MaterialTheme.colorScheme.error) }

            Button(onClick = { vm.salvar(onFechar) }, modifier = Modifier.fillMaxWidth()) { Text("Salvar") }
        }
    }

    if (escolhendoData) {
        val estado = rememberDatePickerState(
            initialSelectedDateMillis = form.data.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
        )
        DatePickerDialog(
            onDismissRequest = { escolhendoData = false },
            confirmButton = {
                TextButton(onClick = {
                    // O DatePicker trabalha em UTC: converter com UTC evita cair no dia anterior.
                    estado.selectedDateMillis?.let { ms ->
                        vm.alterar { it.copy(data = Instant.ofEpochMilli(ms).atZone(ZoneOffset.UTC).toLocalDate()) }
                    }
                    escolhendoData = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { escolhendoData = false }) { Text("Cancelar") } },
        ) { DatePicker(estado) }
    }

    if (confirmarExclusao) {
        AlertDialog(
            onDismissRequest = { confirmarExclusao = false },
            title = { Text("Excluir lançamento?") },
            text = { Text("Esta ação não pode ser desfeita. Se o lançamento veio de uma nota fiscal, a nota e os itens também serão excluídos.") },
            confirmButton = {
                TextButton(onClick = { confirmarExclusao = false; vm.excluir(onFechar) }) { Text("Excluir") }
            },
            dismissButton = { TextButton(onClick = { confirmarExclusao = false }) { Text("Cancelar") } },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SeletorCategoria(categorias: List<Categoria>, selecionada: Long?, onSelecionar: (Long) -> Unit) {
    var aberto by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = aberto, onExpandedChange = { aberto = it }) {
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
