package dev.lucasgola.financas.export

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import dev.lucasgola.financas.data.AppDatabase
import dev.lucasgola.financas.filtro.Filtro
import dev.lucasgola.financas.filtro.Periodo
import dev.lucasgola.financas.util.ZONA
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalDate
import java.time.format.DateTimeFormatter

enum class FormatoExportacao(val extensao: String, val mime: String) {
    CSV("csv", Csv.MIME),
    PDF("pdf", RelatorioPdf.MIME),
}

/** Nome do arquivo a partir do período: financas-2026-10.csv, financas-20260901-20261008.pdf, financas-completo.csv. */
fun nomeArquivo(filtro: Filtro, hoje: LocalDate, formato: FormatoExportacao): String {
    val fmt = DateTimeFormatter.BASIC_ISO_DATE
    val periodo = when (val p = filtro.periodo) {
        is Periodo.Mes -> p.mes.toString()
        Periodo.Tudo -> "completo"
        else -> p.intervalo(hoje)!!.let { "${it.inicio.format(fmt)}-${it.fim.format(fmt)}" }
    }
    return "financas-$periodo.${formato.extensao}"
}

/** Gera o arquivo no cache do app; daí ele é compartilhado ou copiado para onde o usuário escolher. */
class Exportador(private val context: Context, private val db: AppDatabase) {

    private val pasta: File get() = File(context.cacheDir, "exportacoes")

    suspend fun gerar(filtro: Filtro, formato: FormatoExportacao, incluirItens: Boolean): File = withContext(Dispatchers.IO) {
        val hoje = LocalDate.now(ZONA)
        val dados = carregarDadosExportacao(db, filtro, hoje)
        // Só o arquivo mais recente fica no cache.
        pasta.deleteRecursively()
        pasta.mkdirs()
        val arquivo = File(pasta, nomeArquivo(filtro, hoje, formato))
        arquivo.outputStream().use { saida ->
            when (formato) {
                FormatoExportacao.CSV -> saida.write(Csv.gerar(dados, incluirItens).toByteArray(Charsets.UTF_8))
                FormatoExportacao.PDF -> RelatorioPdf.gerar(dados, incluirItens, saida)
            }
        }
        arquivo
    }

    fun intentCompartilhar(arquivo: File, formato: FormatoExportacao): Intent {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.arquivos", arquivo)
        val envio = Intent(Intent.ACTION_SEND).apply {
            type = formato.mime
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, arquivo.nameWithoutExtension)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        return Intent.createChooser(envio, "Compartilhar ${arquivo.name}")
    }

    /** Copia o arquivo gerado para o destino escolhido pelo usuário no seletor do sistema. */
    suspend fun copiarPara(arquivo: File, destino: Uri) = withContext(Dispatchers.IO) {
        val saida = context.contentResolver.openOutputStream(destino) ?: error("Não foi possível abrir o destino")
        saida.use { arquivo.inputStream().use { entrada -> entrada.copyTo(it) } }
    }
}
