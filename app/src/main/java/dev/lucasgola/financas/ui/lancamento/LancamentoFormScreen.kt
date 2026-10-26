package dev.lucasgola.financas.ui.lancamento

import androidx.compose.foundation.layout.Arrangement
import dev.lucasgola.financas.util.formatarMoeda
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.lucasgola.financas.data.AppDatabase
import dev.lucasgola.financas.data.Categoria
import dev.lucasgola.financas.data.CategoriaDao
import dev.lucasgola.financas.data.Estabelecimento
import dev.lucasgola.financas.data.EstabelecimentoDao
import dev.lucasgola.financas.data.Lancamento
import dev.lucasgola.financas.data.LancamentoDao
import dev.lucasgola.financas.data.NotaDao
import dev.lucasgola.financas.data.NotaFiscal
import dev.lucasgola.financas.data.OrigemLancamento
import dev.lucasgola.financas.data.TipoLancamento
import dev.lucasgola.financas.ui.comum.CampoData
import dev.lucasgola.financas.ui.comum.CampoValor
import dev.lucasgola.financas.ui.comum.DialogoCategoriaItem
import dev.lucasgola.financas.ui.comum.LinhaItem
import dev.lucasgola.financas.ui.comum.LinhaTotal
import dev.lucasgola.financas.ui.comum.ListaItensNota
import dev.lucasgola.financas.ui.comum.SeletorCategoria
import dev.lucasgola.financas.util.ZONA
import dev.lucasgola.financas.util.dataLocal
import dev.lucasgola.financas.util.formatarCnpj
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
import java.time.format.DateTimeFormatter
import dev.lucasgola.financas.data.MetodoPagamento
import dev.lucasgola.financas.data.MetodoPagamentoDao
import dev.lucasgola.financas.ui.comum.SeletorMetodoPagamento

data class FormLancamento(
    val tipo: TipoLancamento = TipoLancamento.SAIDA,
    val valorTexto: String = "",
    val data: LocalDate = LocalDate.now(ZONA),
    val categoriaId: Long? = null,
    val descricao: String = "",
    val formaPagamento: String = "",
    val observacao: String = "",
    val erro: String? = null,
    val metodoPagamentoId: Long? = null,
)

/** Nota fiscal vinculada ao lançamento (só leitura no formulário). */
data class NotaVinculada(
    val nota: NotaFiscal,
    val itens: List<LinhaItem>,
    val estabelecimento: Estabelecimento?,
    val formaPagamento: String?,
)

class LancamentoFormViewModel(
    private val lancamentoDao: LancamentoDao,
    private val notaDao: NotaDao,
    private val estabelecimentoDao: EstabelecimentoDao,
    categoriaDao: CategoriaDao,
    metodoPagamentoDao: MetodoPagamentoDao,
    private val id: Long?,
) : ViewModel() {

    private val _form = MutableStateFlow(FormLancamento())
    val form: StateFlow<FormLancamento> = _form.asStateFlow()

    private val _nota = MutableStateFlow<NotaVinculada?>(null)
    val nota: StateFlow<NotaVinculada?> = _nota.asStateFlow()

    val categorias: StateFlow<List<Categoria>> = categoriaDao.observarTodas()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val metodos: StateFlow<List<MetodoPagamento>> = metodoPagamentoDao.observarTodos()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Lançamento de nota fiscal: a forma de pagamento é o texto da nota. */
    val daNota: Boolean get() = original?.origem == OrigemLancamento.NFCE

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
                    metodoPagamentoId = l.metodoPagamentoId,
                )
            }
            notaDao.buscarPorLancamento(id)?.let { n ->
                _nota.value = NotaVinculada(
                    nota = n,
                    itens = notaDao.itens(n.id).map {
                        LinhaItem(
                            it.descricao, it.codigo, it.quantidade, it.unidade, it.valorUnitario, it.valorTotalCentavos,
                            id = it.id, categoriaId = it.categoriaId,
                        )
                    },
                    estabelecimento = original?.estabelecimentoId?.let { estabelecimentoDao.buscar(it) },
                    formaPagamento = original?.formaPagamento,
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
                metodoPagamentoId = f.metodoPagamentoId,
                criadoEm = o?.criadoEm ?: Instant.now(),
                atualizadoEm = Instant.now(),
            )
            if (o == null) lancamentoDao.inserir(novo) else lancamentoDao.atualizar(novo)
            // Corrigir o método depois de importar (ex.: era VR, não débito) vale para as próximas notas da loja.
            if (o?.estabelecimentoId != null && o.metodoPagamentoId != novo.metodoPagamentoId) {
                estabelecimentoDao.buscar(o.estabelecimentoId)?.let {
                    estabelecimentoDao.atualizar(it.copy(metodoPagamentoPadraoId = novo.metodoPagamentoId))
                }
            }
            onOk()
        }
    }

    /** Classifica um item numa categoria própria; null devolve o item à categoria do lançamento. */
    fun classificarItem(item: LinhaItem, categoriaId: Long?) {
        val atual = _nota.value ?: return
        // Escolher a própria categoria do lançamento é o mesmo que não classificar.
        val nova = categoriaId?.takeIf { it != original?.categoriaId }
        viewModelScope.launch {
            notaDao.classificarItem(item.id, nova)
            _nota.value = atual.copy(itens = atual.itens.map { if (it.id == item.id) it.copy(categoriaId = nova) else it })
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
        LancamentoFormViewModel(
            db.lancamentoDao(), db.notaDao(), db.estabelecimentoDao(), db.categoriaDao(), db.metodoPagamentoDao(), lancamentoId,
        )
    }
    val form by vm.form.collectAsStateWithLifecycle()
    val categorias by vm.categorias.collectAsStateWithLifecycle()
    val metodos by vm.metodos.collectAsStateWithLifecycle()
    val nota by vm.nota.collectAsStateWithLifecycle()

    var confirmarExclusao by remember { mutableStateOf(false) }
    var classificando by remember { mutableStateOf<LinhaItem?>(null) }

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

            CampoValor(form.valorTexto, { v -> vm.alterar { it.copy(valorTexto = v) } })
            CampoData(form.data, { d -> vm.alterar { it.copy(data = d) } })
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
            SeletorMetodoPagamento(
                metodos = metodos.filter { it.ativo || it.id == form.metodoPagamentoId },
                selecionado = form.metodoPagamentoId,
                onSelecionar = { id -> vm.alterar { it.copy(metodoPagamentoId = id) } },
            )
            // O texto livre antigo só aparece em nota fiscal (o que a SEFAZ registrou) ou se já tiver valor.
            if (vm.daNota || form.formaPagamento.isNotBlank()) OutlinedTextField(
                value = form.formaPagamento,
                onValueChange = { v -> vm.alterar { it.copy(formaPagamento = v) } },
                label = { Text(if (vm.daNota) "Forma de pagamento na nota" else "Forma de pagamento") },
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

            nota?.let { n ->
                NotaDoLancamento(
                    n,
                    nomeCategoria = { id -> categorias.firstOrNull { it.id == id }?.nome },
                    onClicarItem = { classificando = it },
                )
            }
        }
    }

    classificando?.let { item ->
        DialogoCategoriaItem(
            item = item,
            categorias = categorias.filter { (it.ativa && it.tipo.serveParaItem) || it.id == item.categoriaId },
            categoriaDaNota = categorias.firstOrNull { it.id == form.categoriaId }?.nome,
            onEscolher = { id -> classificando = null; vm.classificarItem(item, id) },
            onCancelar = { classificando = null },
        )
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

private val fmtEmissao = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")

@Composable
private fun NotaDoLancamento(v: NotaVinculada, nomeCategoria: (Long) -> String?, onClicarItem: (LinhaItem) -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            v.estabelecimento?.let { e ->
                Text(e.razaoSocial, style = MaterialTheme.typography.titleSmall)
                Text(
                    listOf("CNPJ ${formatarCnpj(e.cnpj)}", e.endereco).filter { it.isNotBlank() }.joinToString("\n"),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                listOfNotNull(
                    "NFC-e nº ${v.nota.numero} · série ${v.nota.serie}",
                    v.nota.dataEmissao?.let { "Emitida em ${it.atZone(ZONA).format(fmtEmissao)}" },
                    v.formaPagamento?.let { "Pagamento: $it" },
                ).joinToString("\n"),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (v.itens.isEmpty()) {
                Text(
                    "Nota sem itens (valor informado manualmente).",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Text(
                    "Toque num item para colocá-lo em outra categoria (opcional). Ex.: a carne da compra do mercado.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                ListaItensNota(v.itens, nomeCategoria = nomeCategoria, onClicar = onClicarItem)
                v.nota.valorBrutoCentavos?.let { LinhaTotal("Subtotal", it) }
                if (v.nota.descontoCentavos > 0) LinhaTotal("Descontos", -v.nota.descontoCentavos)
                v.nota.valorTotalCentavos?.let { LinhaTotal("Total pago", it, destaque = true) }
                v.nota.valorTotalCentavos?.let { total ->
                    val conferido = v.itens.sumOf { it.valorTotalCentavos } - v.nota.descontoCentavos
                    if (conferido != total) Text(
                        "A soma dos itens menos os descontos (${formatarMoeda(conferido)}) não bate com o total pago. " +
                            "Os dados estão como vieram da SEFAZ.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }
    }
}
