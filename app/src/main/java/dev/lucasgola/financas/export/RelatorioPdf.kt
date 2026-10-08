package dev.lucasgola.financas.export

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import dev.lucasgola.financas.util.LOCALE_BR
import dev.lucasgola.financas.util.ZONA
import dev.lucasgola.financas.util.formatarMoeda
import java.io.OutputStream
import java.text.NumberFormat
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

/** Desenha o relatório (A4) a partir do layout calculado em [RelatorioLayout]. */
object RelatorioPdf {
    const val MIME = "application/pdf"

    // A4 em pontos (1/72 pol.).
    private const val LARGURA = 595
    private const val ALTURA = 842
    private const val MARGEM = 40f
    private const val RODAPE = 24f

    // Colunas da tabela de lançamentos.
    private const val COL_DATA = 58f
    private const val COL_CATEGORIA = 110f
    private const val COL_VALOR = 80f

    private val preto = 0xFF1B1B1B.toInt()
    private val cinza = 0xFF6B6B6B.toInt()
    private val linhaCinza = 0xFFDDDDDD.toInt()
    private val fundoCabecalho = 0xFFF1F1EE.toInt()

    private val fmtGerado = DateTimeFormatter.ofPattern("dd/MM/yyyy 'às' HH:mm")

    fun gerar(dados: DadosExportacao, incluirItens: Boolean, saida: OutputStream) {
        val texto = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = preto; textSize = RelatorioLayout.TAMANHO_TEXTO }
        val medidor = MedidorTexto { t, tamanho -> texto.textSize = tamanho; texto.measureText(t) }
        val larguraUtil = LARGURA - 2 * MARGEM

        val linhas = RelatorioLayout.montar(dados, incluirItens, larguraUtil, medidor)
        val paginas = RelatorioLayout.paginar(linhas, ALTURA - 2 * MARGEM - RODAPE)
        val gerado = ZonedDateTime.now(ZONA).format(fmtGerado)

        val doc = PdfDocument()
        try {
            paginas.forEachIndexed { indice, conteudo ->
                val pagina = doc.startPage(PdfDocument.PageInfo.Builder(LARGURA, ALTURA, indice + 1).create())
                val c = pagina.canvas
                var y = MARGEM
                conteudo.forEach { linha ->
                    desenhar(c, linha, y, larguraUtil, medidor)
                    y += linha.altura
                }
                rodape(c, "Finanças · gerado em $gerado", "Página ${indice + 1} de ${paginas.size}")
                doc.finishPage(pagina)
            }
            doc.writeTo(saida)
        } finally {
            doc.close()
        }
    }

    private fun paint(tamanho: Float, cor: Int = preto, negrito: Boolean = false, alinhamento: Paint.Align = Paint.Align.LEFT) =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = tamanho
            color = cor
            textAlign = alinhamento
            typeface = if (negrito) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
        }

    /** [y] é o topo da linha; o texto é posicionado pela linha de base. */
    private fun desenhar(c: Canvas, linha: LinhaRelatorio, y: Float, largura: Float, m: MedidorTexto) {
        val esq = MARGEM
        val dir = MARGEM + largura
        val t = RelatorioLayout.TAMANHO_TEXTO
        when (linha) {
            is LinhaRelatorio.Titulo -> c.drawText(linha.texto, esq, y + 16f, paint(16f, negrito = true))
            is LinhaRelatorio.Texto -> c.drawText(linha.texto, esq, y + 10f, paint(t, cinza))
            is LinhaRelatorio.Secao -> {
                c.drawText(linha.titulo, esq, y + 18f, paint(11f, negrito = true))
                c.drawLine(esq, y + 22f, dir, y + 22f, Paint().apply { color = linhaCinza; strokeWidth = 0.5f })
            }
            is LinhaRelatorio.Resumo -> {
                val col = largura / 3
                listOf(
                    "Entradas" to linha.entradas,
                    "Saídas" to linha.saidas,
                    "Saldo" to linha.entradas - linha.saidas,
                ).forEachIndexed { i, (rotulo, valor) ->
                    c.drawText(rotulo, esq + col * i, y + 11f, paint(t, cinza))
                    c.drawText(formatarMoeda(valor), esq + col * i, y + 26f, paint(12f, negrito = true))
                }
            }
            is LinhaRelatorio.Categoria -> {
                c.drawText(RelatorioLayout.encurtar(linha.nome, largura - 160f, t, m), esq, y + 11f, paint(t))
                c.drawText(percentual(linha.fracao), dir - 100f, y + 11f, paint(t, cinza, alinhamento = Paint.Align.RIGHT))
                c.drawText(formatarMoeda(linha.valor), dir, y + 11f, paint(t, alinhamento = Paint.Align.RIGHT))
            }
            LinhaRelatorio.CabecalhoTabela -> {
                c.drawRect(esq, y + 2f, dir, y + linha.altura - 1f, Paint().apply { color = fundoCabecalho })
                val p = paint(t, negrito = true)
                c.drawText("Data", esq + 4f, y + 13f, p)
                c.drawText("Descrição", esq + COL_DATA, y + 13f, p)
                c.drawText("Categoria", dir - COL_VALOR - COL_CATEGORIA, y + 13f, p)
                c.drawText("Valor", dir - 4f, y + 13f, paint(t, negrito = true, alinhamento = Paint.Align.RIGHT))
            }
            is LinhaRelatorio.Lancamento -> {
                val larguraDesc = largura - COL_DATA - COL_CATEGORIA - COL_VALOR - 8f
                c.drawText(linha.data, esq + 4f, y + 11f, paint(t))
                c.drawText(RelatorioLayout.encurtar(linha.descricao, larguraDesc, t, m), esq + COL_DATA, y + 11f, paint(t))
                c.drawText(
                    RelatorioLayout.encurtar(linha.categoria, COL_CATEGORIA - 8f, t, m),
                    dir - COL_VALOR - COL_CATEGORIA, y + 11f, paint(t, cinza),
                )
                val valor = (if (linha.valorComSinal < 0) "− " else "+ ") + formatarMoeda(kotlin.math.abs(linha.valorComSinal))
                c.drawText(valor, dir - 4f, y + 11f, paint(t, alinhamento = Paint.Align.RIGHT))
                c.drawLine(esq, y + linha.altura - 0.5f, dir, y + linha.altura - 0.5f, Paint().apply { color = linhaCinza; strokeWidth = 0.3f })
            }
            is LinhaRelatorio.Item -> {
                val tam = t - 1f
                val x = esq + COL_DATA + 8f
                val larguraItem = largura - COL_DATA - COL_VALOR - 150f
                c.drawText(RelatorioLayout.encurtar(linha.descricao, larguraItem, tam, m), x, y + 9f, paint(tam, cinza))
                c.drawText(linha.detalhe, dir - COL_VALOR - 8f, y + 9f, paint(tam, cinza, alinhamento = Paint.Align.RIGHT))
                c.drawText(formatarMoeda(linha.valor), dir - 4f, y + 9f, paint(tam, cinza, alinhamento = Paint.Align.RIGHT))
            }
            is LinhaRelatorio.Espaco -> Unit
        }
    }

    private fun rodape(c: Canvas, esquerda: String, direita: String) {
        val y = ALTURA - MARGEM + 8f
        c.drawText(esquerda, MARGEM, y, paint(7.5f, cinza))
        c.drawText(direita, LARGURA - MARGEM, y, paint(7.5f, cinza, alinhamento = Paint.Align.RIGHT))
    }

    private fun percentual(fracao: Double): String =
        NumberFormat.getPercentInstance(LOCALE_BR).apply { maximumFractionDigits = 0 }.format(fracao)
}
