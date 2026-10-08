package dev.lucasgola.financas.backup

import android.content.Context
import android.net.Uri
import androidx.room.withTransaction
import dev.lucasgola.financas.data.AppDatabase
import dev.lucasgola.financas.data.TipoLancamento
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate

/** Contagens e totais, mostrados antes de restaurar para o usuário comparar o arquivo com o que já tem. */
data class ResumoBackup(
    val lancamentos: Int,
    val notas: Int,
    val itens: Int,
    val entradasCentavos: Long,
    val saidasCentavos: Long,
)

fun Backup.resumo() = ResumoBackup(
    lancamentos = lancamentos.size,
    notas = notas.size,
    itens = itens.size,
    entradasCentavos = lancamentos.filter { it.tipo == TipoLancamento.ENTRADA }.sumOf { it.valorCentavos },
    saidasCentavos = lancamentos.filter { it.tipo == TipoLancamento.SAIDA }.sumOf { it.valorCentavos },
)

fun nomeArquivoBackup(hoje: LocalDate) = "financas-backup-$hoje.json"

class BackupRepository(private val context: Context, private val db: AppDatabase) {
    private val dao = db.backupDao()

    /** Lê tudo numa transação, para o backup ser um retrato consistente do banco. */
    suspend fun atual(): Backup = db.withTransaction {
        Backup(
            geradoEm = Instant.now(),
            categorias = dao.categorias(),
            estabelecimentos = dao.estabelecimentos(),
            lancamentos = dao.lancamentos(),
            notas = dao.notas(),
            itens = dao.itens(),
        )
    }

    suspend fun salvar(destino: Uri) {
        val json = BackupJson.gerar(atual())
        withContext(Dispatchers.IO) {
            val saida = context.contentResolver.openOutputStream(destino, "wt") ?: error("Não foi possível abrir o destino")
            saida.use { it.write(json.toByteArray(Charsets.UTF_8)) }
        }
    }

    suspend fun ler(origem: Uri): Backup = withContext(Dispatchers.IO) {
        val entrada = context.contentResolver.openInputStream(origem) ?: error("Não foi possível abrir o arquivo")
        BackupJson.ler(entrada.use { it.readBytes().toString(Charsets.UTF_8) })
    }

    /**
     * Substitui todos os dados pelos do backup. Tudo numa transação: se qualquer linha falhar
     * (ex.: referência quebrada num arquivo editado à mão), nada muda.
     */
    suspend fun restaurar(b: Backup) = db.withTransaction {
        dao.limparItens()
        dao.limparNotas()
        dao.limparLancamentos()
        dao.limparEstabelecimentos()
        dao.limparCategorias()
        dao.inserirCategorias(b.categorias)
        dao.inserirEstabelecimentos(b.estabelecimentos)
        dao.inserirLancamentos(b.lancamentos)
        dao.inserirNotas(b.notas)
        dao.inserirItens(b.itens)
    }
}
