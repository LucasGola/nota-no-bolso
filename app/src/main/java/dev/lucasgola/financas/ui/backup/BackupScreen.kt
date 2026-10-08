package dev.lucasgola.financas.ui.backup

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.lucasgola.financas.backup.Backup
import dev.lucasgola.financas.backup.BackupInvalidoException
import dev.lucasgola.financas.backup.BackupJson
import dev.lucasgola.financas.backup.BackupRepository
import dev.lucasgola.financas.backup.ResumoBackup
import dev.lucasgola.financas.backup.nomeArquivoBackup
import dev.lucasgola.financas.backup.resumo
import dev.lucasgola.financas.filtro.Filtro
import dev.lucasgola.financas.util.ZONA
import dev.lucasgola.financas.util.formatarMoeda
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter

class BackupViewModel(private val repo: BackupRepository, private val filtro: MutableStateFlow<Filtro>) : ViewModel() {
    val atual = MutableStateFlow<ResumoBackup?>(null)
    /** Backup lido do arquivo, aguardando confirmação para substituir os dados. */
    val lido = MutableStateFlow<Backup?>(null)
    val ocupado = MutableStateFlow(false)
    val mensagem = MutableStateFlow<String?>(null)

    init { recarregar() }

    private fun recarregar() = viewModelScope.launch { atual.value = repo.atual().resumo() }

    private fun executar(falha: String, bloco: suspend () -> Unit) {
        ocupado.value = true
        viewModelScope.launch {
            try {
                bloco()
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                mensagem.value = if (e is BackupInvalidoException) e.message else "$falha: ${e.message}"
            } finally {
                ocupado.value = false
            }
        }
    }

    fun salvar(destino: Uri) = executar("Não foi possível salvar o backup") {
        repo.salvar(destino)
        mensagem.value = "Backup salvo."
    }

    fun ler(origem: Uri) = executar("Não foi possível ler o arquivo") { lido.value = repo.ler(origem) }

    fun cancelarRestauracao() { lido.value = null }

    fun restaurar() {
        val backup = lido.value ?: return
        lido.value = null
        executar("Não foi possível restaurar; nenhum dado foi alterado") {
            repo.restaurar(backup)
            // O filtro pode apontar para categorias/estabelecimentos que não existem mais.
            filtro.value = Filtro()
            atual.value = repo.atual().resumo()
            mensagem.value = "Backup restaurado."
        }
    }
}

private val fmtDataHora = DateTimeFormatter.ofPattern("dd/MM/yyyy 'às' HH:mm")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackupScreen(repo: BackupRepository, filtro: MutableStateFlow<Filtro>, onFechar: () -> Unit) {
    val vm: BackupViewModel = viewModel { BackupViewModel(repo, filtro) }
    val atual by vm.atual.collectAsStateWithLifecycle()
    val lido by vm.lido.collectAsStateWithLifecycle()
    val ocupado by vm.ocupado.collectAsStateWithLifecycle()
    val mensagem by vm.mensagem.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(mensagem) {
        mensagem?.let { snackbar.showSnackbar(it); vm.mensagem.value = null }
    }

    val salvar = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(BackupJson.MIME)) { it?.let(vm::salvar) }
    // Alguns gerenciadores de arquivo não reconhecem .json como application/json; aceitar qualquer tipo
    // e validar o conteúdo evita um arquivo de backup que aparece cinza e não pode ser escolhido.
    val abrir = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { it?.let(vm::ler) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Backup") },
                navigationIcon = { IconButton(onClick = onFechar) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Voltar") } },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (ocupado) LinearProgressIndicator(Modifier.fillMaxWidth())

            Secao("Backup automático do Android") {
                Texto(
                    "Se o backup do Google estiver ativado no aparelho (Configurações › Google › Backup), " +
                        "o Android copia os dados deste app para a sua conta, em geral uma vez por dia, " +
                        "com o aparelho carregando e no Wi-Fi. Ao reinstalar o app ou configurar um celular novo " +
                        "com a mesma conta, os dados voltam sozinhos.",
                )
                Texto("Ele não deixa escolher quando nem guardar versões antigas. Para isso, use o arquivo de backup abaixo.")
            }

            Secao("Arquivo de backup") {
                Texto(
                    "Salva todos os lançamentos, notas, itens e categorias num arquivo .json. " +
                        "Guarde-o fora do celular (Drive, e-mail, computador).",
                )
                atual?.let { Text("Agora no app: ${descrever(it)}", style = MaterialTheme.typography.bodyMedium) }
                Button(
                    enabled = !ocupado,
                    onClick = { salvar.launch(nomeArquivoBackup(LocalDate.now(ZONA))) },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Salvar backup…") }
                OutlinedButton(
                    enabled = !ocupado,
                    onClick = { abrir.launch(arrayOf("*/*")) },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Restaurar de um arquivo…") }
            }
        }
    }

    lido?.let { backup ->
        val resumo = backup.resumo()
        AlertDialog(
            onDismissRequest = vm::cancelarRestauracao,
            title = { Text("Substituir todos os dados?") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Backup de ${backup.geradoEm.atZone(ZONA).format(fmtDataHora)}:", fontWeight = FontWeight.SemiBold)
                    Text(descrever(resumo))
                    Text(
                        "Entradas ${formatarMoeda(resumo.entradasCentavos)} · Saídas ${formatarMoeda(resumo.saidasCentavos)}",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    atual?.let { Text("Agora no app: ${descrever(it)}", style = MaterialTheme.typography.bodySmall) }
                    Text(
                        "Tudo o que está no app hoje será apagado e trocado pelo conteúdo do backup. Não dá para desfazer: " +
                            "se quiser guardar os dados atuais, cancele e salve um backup antes.",
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = vm::restaurar,
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                ) { Text("Substituir") }
            },
            dismissButton = { TextButton(onClick = vm::cancelarRestauracao) { Text("Cancelar") } },
        )
    }
}

private fun descrever(r: ResumoBackup) =
    "${r.lancamentos} lançamento(s), ${r.notas} nota(s) fiscal(is), ${r.itens} item(ns)"

@Composable
private fun Secao(titulo: String, conteudo: @Composable () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(titulo, style = MaterialTheme.typography.titleMedium)
            conteudo()
        }
    }
}

@Composable
private fun Texto(t: String) =
    Text(t, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
