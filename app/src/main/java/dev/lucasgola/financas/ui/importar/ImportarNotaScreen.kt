package dev.lucasgola.financas.ui.importar

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.lucasgola.financas.data.Categoria
import dev.lucasgola.financas.data.CategoriaDao
import dev.lucasgola.financas.data.TipoLancamento
import dev.lucasgola.financas.nfce.ImportacaoRepository
import dev.lucasgola.financas.nfce.NotaImportada
import dev.lucasgola.financas.nfce.Preparo
import dev.lucasgola.financas.nfce.dataEmissaoInstant
import dev.lucasgola.financas.ui.comum.CampoData
import dev.lucasgola.financas.ui.comum.DialogoCategoriaItem
import dev.lucasgola.financas.ui.comum.LinhaItem
import dev.lucasgola.financas.ui.comum.LinhaTotal
import dev.lucasgola.financas.ui.comum.ListaItensNota
import dev.lucasgola.financas.ui.comum.SeletorCategoria
import dev.lucasgola.financas.util.dataLocal
import dev.lucasgola.financas.util.formatarCnpj
import dev.lucasgola.financas.util.formatarMoeda
import dev.lucasgola.financas.util.meioDia
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import android.content.ContentResolver
import android.net.Uri
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import dev.lucasgola.financas.data.MetodoPagamento
import dev.lucasgola.financas.data.MetodoPagamentoDao
import dev.lucasgola.financas.ui.comum.SeletorMetodoPagamento

sealed interface EstadoImportacao {
    data object Consultando : EstadoImportacao
    data class Invalida(val mensagem: String) : EstadoImportacao
    data class JaImportada(val lancamentoId: Long) : EstadoImportacao
    data class Falhou(val mensagem: String) : EstadoImportacao
    /** Segunda tentativa: o usuário salva a página da nota no navegador e a entrega ao app. */
    data class PaginaSalva(val url: String, val erro: String? = null) : EstadoImportacao
    data class Revisao(
        val nota: NotaImportada,
        val urlQr: String,
        val categoriaId: Long?,
        val descricao: String,
        val data: LocalDate,
        val metodoPagamentoId: Long? = null,
        /** Categoria própria de cada item (pela ordem na nota); ausente = a mesma do lançamento. */
        val categoriasItens: Map<Int, Long> = emptyMap(),
        val salvando: Boolean = false,
        val erro: String? = null,
    ) : EstadoImportacao
}

class ImportarNotaViewModel(
    private val repo: ImportacaoRepository,
    categoriaDao: CategoriaDao,
    metodoPagamentoDao: MetodoPagamentoDao,
    private val conteudoQr: String,
) : ViewModel() {

    private val _estado = MutableStateFlow<EstadoImportacao>(EstadoImportacao.Consultando)
    val estado: StateFlow<EstadoImportacao> = _estado.asStateFlow()

    val categorias: StateFlow<List<Categoria>> = categoriaDao.observarTodas()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val metodos: StateFlow<List<MetodoPagamento>> = metodoPagamentoDao.observarTodos()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        viewModelScope.launch { _estado.value = paraEstado(repo.preparar(conteudoQr)) }
    }

    /** Lê a página da nota salva pelo navegador. */
    fun paginaSalva(arquivo: Uri, resolver: ContentResolver) {
        val anterior = _estado.value as? EstadoImportacao.PaginaSalva ?: return
        _estado.value = EstadoImportacao.Consultando
        viewModelScope.launch {
            val bytes = try {
                withContext(Dispatchers.IO) { resolver.openInputStream(arquivo)?.use { it.readBytes() } }
            } catch (e: Exception) {
                null
            }
            _estado.value = if (bytes == null) anterior.copy(erro = "Não foi possível abrir o arquivo.")
            else paraEstado(repo.prepararDaPagina(conteudoQr, bytes))
        }
    }

    private fun paraEstado(p: Preparo): EstadoImportacao = when (p) {
        is Preparo.Invalida -> EstadoImportacao.Invalida(p.mensagem)
        is Preparo.JaImportada -> EstadoImportacao.JaImportada(p.lancamentoId)
        is Preparo.Falhou -> EstadoImportacao.Falhou(p.mensagem)
        is Preparo.PrecisaDaPagina -> EstadoImportacao.PaginaSalva(p.url, p.erro)
        is Preparo.Pronta -> EstadoImportacao.Revisao(
            nota = p.nota,
            urlQr = p.urlQr,
            categoriaId = p.categoriaSugeridaId,
            descricao = p.nota.emitente.razaoSocial,
            data = p.nota.dataEmissao.toLocalDate(),
            metodoPagamentoId = p.metodoSugeridoId,
        )
    }

    fun alterar(f: (EstadoImportacao.Revisao) -> EstadoImportacao.Revisao) = _estado.update {
        if (it is EstadoImportacao.Revisao) f(it).copy(erro = null) else it
    }

    /** Escolher a própria categoria do lançamento (ou null) é o mesmo que não classificar. */
    fun classificarItem(ordem: Int, categoriaId: Long?) = alterar { r ->
        val nova = categoriaId?.takeIf { it != r.categoriaId }
        r.copy(categoriasItens = if (nova == null) r.categoriasItens - ordem else r.categoriasItens + (ordem to nova))
    }

    fun salvar(onSalvo: () -> Unit) {
        val r = _estado.value as? EstadoImportacao.Revisao ?: return
        val categoriaId = r.categoriaId
        if (categoriaId == null) {
            _estado.value = r.copy(erro = "Escolha uma categoria")
            return
        }
        _estado.value = r.copy(salvando = true)
        viewModelScope.launch {
            // Mantém a hora da emissão se a data não foi alterada.
            val emissao = r.nota.dataEmissaoInstant
            val dataHora = if (emissao.dataLocal() == r.data) emissao else r.data.meioDia()
            try {
                repo.confirmar(r.nota, r.urlQr, categoriaId, r.descricao.trim().ifEmpty { r.nota.emitente.razaoSocial }, dataHora, r.metodoPagamentoId, r.categoriasItens)
                onSalvo()
            } catch (e: Exception) {
                _estado.value = r.copy(salvando = false, erro = "Não foi possível salvar: ${e.message}")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImportarNotaScreen(
    repo: ImportacaoRepository,
    categoriaDao: CategoriaDao,
    metodoPagamentoDao: MetodoPagamentoDao,
    conteudoQr: String,
    onFechar: () -> Unit,
    onAbrirLancamento: (Long) -> Unit,
    onVerPendentes: () -> Unit,
) {
    val vm: ImportarNotaViewModel = viewModel(key = "importar-$conteudoQr") {
        ImportarNotaViewModel(repo, categoriaDao, metodoPagamentoDao, conteudoQr)
    }
    val estado by vm.estado.collectAsStateWithLifecycle()
    val categorias by vm.categorias.collectAsStateWithLifecycle()
    val metodos by vm.metodos.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Importar nota") },
                navigationIcon = { IconButton(onClick = onFechar) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Voltar") } },
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when (val e = estado) {
                EstadoImportacao.Consultando -> Centro {
                    CircularProgressIndicator()
                    Text("Consultando a nota na SEFAZ…")
                }
                is EstadoImportacao.Invalida -> Centro {
                    Text(e.mensagem, textAlign = TextAlign.Center)
                    Button(onClick = onFechar) { Text("Voltar") }
                }
                is EstadoImportacao.JaImportada -> Centro {
                    Text("Esta nota já foi importada.", textAlign = TextAlign.Center)
                    Button(onClick = { onAbrirLancamento(e.lancamentoId) }) { Text("Abrir lançamento") }
                    OutlinedButton(onClick = onFechar) { Text("Voltar") }
                }
                is EstadoImportacao.Falhou -> Centro {
                    Text(e.mensagem, textAlign = TextAlign.Center)
                    Text(
                        "A nota foi guardada como pendente. Você pode tentar de novo mais tarde ou informar o valor à mão.",
                        style = MaterialTheme.typography.bodySmall,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Button(onClick = onVerPendentes) { Text("Ver pendentes") }
                    OutlinedButton(onClick = onFechar) { Text("Voltar") }
                }
                is EstadoImportacao.PaginaSalva -> {
                    val resolver = LocalContext.current.contentResolver
                    PaginaSalvaSefaz(e.url, e.erro, { vm.paginaSalva(it, resolver) }, onFechar)
                }
                is EstadoImportacao.Revisao -> Revisao(
                    r = e,
                    categorias = categorias.filter { (it.ativa && it.tipo.aceita(TipoLancamento.SAIDA)) || it.id == e.categoriaId },
                    todasCategorias = categorias,
                    metodos = metodos.filter { it.ativo },
                    onAlterar = vm::alterar,
                    onClassificarItem = vm::classificarItem,
                    onSalvar = { vm.salvar(onFechar) },
                )
            }
        }
    }
}

@Composable
private fun Centro(conteudo: @Composable () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) { conteudo() }
}

private val fmtEmissao = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")

@Composable
private fun Revisao(
    r: EstadoImportacao.Revisao,
    categorias: List<Categoria>,
    todasCategorias: List<Categoria>,
    metodos: List<MetodoPagamento>,
    onAlterar: ((EstadoImportacao.Revisao) -> EstadoImportacao.Revisao) -> Unit,
    onClassificarItem: (ordem: Int, categoriaId: Long?) -> Unit,
    onSalvar: () -> Unit,
) {
    val n = r.nota
    var classificando by remember { mutableStateOf<LinhaItem?>(null) }
    Column(
        Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column {
            Text(n.emitente.razaoSocial, style = MaterialTheme.typography.titleMedium)
            Text(
                "CNPJ ${formatarCnpj(n.emitente.cnpj)}\n${n.emitente.endereco}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                "Emitida em ${n.dataEmissao.format(fmtEmissao)} · NFC-e nº ${n.numero}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Text(formatarMoeda(n.valorAPagarCentavos), style = MaterialTheme.typography.headlineMedium)

        if (!n.totaisConferem) {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                Text(
                    "Atenção: a soma dos itens (${formatarMoeda(n.itens.sumOf { it.valorTotalCentavos })}) menos descontos " +
                        "não bate com o total da nota. Os dados serão salvos como vieram da SEFAZ.",
                    Modifier.padding(12.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                )
            }
        }

        SeletorCategoria(categorias, r.categoriaId, { id -> onAlterar { it.copy(categoriaId = id) } })
        OutlinedTextField(
            value = r.descricao,
            onValueChange = { v -> onAlterar { it.copy(descricao = v) } },
            label = { Text("Descrição") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        CampoData(r.data, { d -> onAlterar { it.copy(data = d) } })
        SeletorMetodoPagamento(metodos, r.metodoPagamentoId, { id -> onAlterar { it.copy(metodoPagamentoId = id) } })

        r.erro?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        Button(onClick = onSalvar, enabled = !r.salvando, modifier = Modifier.fillMaxWidth()) {
            Text(if (r.salvando) "Salvando…" else "Salvar")
        }

        HorizontalDivider()
        Text("${n.itens.size} itens", style = MaterialTheme.typography.titleSmall)
        Text(
            "Toque num item para colocá-lo em outra categoria (opcional). Ex.: a carne da compra do mercado.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        ListaItensNota(
            // Antes de salvar os itens não têm id: a ordem na nota faz esse papel.
            n.itens.map {
                LinhaItem(
                    it.descricao, it.codigo, it.quantidade, it.unidade, it.valorUnitario, it.valorTotalCentavos,
                    id = it.ordem.toLong(), categoriaId = r.categoriasItens[it.ordem],
                )
            },
            nomeCategoria = { id -> todasCategorias.firstOrNull { it.id == id }?.nome },
            onClicar = { classificando = it },
        )
        HorizontalDivider()
        n.valorBrutoCentavos?.let { LinhaTotal("Subtotal", it) }
        if (n.descontoCentavos > 0) LinhaTotal("Descontos", -n.descontoCentavos)
        LinhaTotal("Total pago", n.valorAPagarCentavos, destaque = true)
        n.pagamentos.forEach { LinhaTotal(it.forma, it.valorCentavos) }
    }

    classificando?.let { item ->
        DialogoCategoriaItem(
            item = item,
            categorias = todasCategorias.filter { (it.ativa && it.tipo.serveParaItem) || it.id == item.categoriaId },
            categoriaDaNota = todasCategorias.firstOrNull { it.id == r.categoriaId }?.nome,
            onEscolher = { id -> classificando = null; onClassificarItem(item.id.toInt(), id) },
            onCancelar = { classificando = null },
        )
    }
}
