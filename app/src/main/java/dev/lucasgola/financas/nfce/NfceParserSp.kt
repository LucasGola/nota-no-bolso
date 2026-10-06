package dev.lucasgola.financas.nfce

import dev.lucasgola.financas.util.parseCentavosBr
import dev.lucasgola.financas.util.parseDecimalBr
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

class NfceParseException(mensagem: String) : Exception(mensagem)

/**
 * Parser da página "Consulta Resumida NFC-e" da SEFAZ-SP (ConsultaQRCode.aspx).
 * Depende do layout HTML da SEFAZ: quando ele mudar, os testes com fixtures reais devem quebrar primeiro.
 */
object NfceParserSp {

    private val formatoEmissao = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss")

    fun parse(html: String): NotaImportada = parse(Jsoup.parse(html))

    fun parse(doc: Document): NotaImportada {
        if (doc.getElementById("hdfNotaCancelada") != null) throw NfceParseException("Nota cancelada")
        if (doc.getElementById("hdfNotaDenegada") != null) throw NfceParseException("Nota denegada")

        val tabela = doc.getElementById("tabResult")
            ?: throw NfceParseException("Página da SEFAZ sem a tabela de itens (layout mudou ou consulta falhou)")

        val chave = doc.selectFirst("span.chave")?.text()?.let(ChaveAcesso::of)
            ?: throw NfceParseException("Chave de acesso não encontrada na página")

        val infos = doc.getElementById("infos")?.text().orEmpty()
        val emissao = Regex("""Emissão:\s*(\d{2}/\d{2}/\d{4} \d{2}:\d{2}:\d{2})""").find(infos)
            ?.groupValues?.get(1)?.let { LocalDateTime.parse(it, formatoEmissao) }
            ?: throw NfceParseException("Data de emissão não encontrada")
        val numero = Regex("""Número:\s*(\d+)""").find(infos)?.groupValues?.get(1)?.toLong() ?: chave.numero
        val serie = Regex("""Série:\s*(\d+)""").find(infos)?.groupValues?.get(1)?.toInt() ?: chave.serie

        val itens = tabela.select("tr").mapIndexed { i, tr -> parseItem(i + 1, tr) }
        if (itens.isEmpty()) throw NfceParseException("Nenhum item encontrado")

        val totais = parseTotais(doc)

        return NotaImportada(
            chave = chave,
            emitente = parseEmitente(doc),
            numero = numero,
            serie = serie,
            dataEmissao = emissao,
            itens = itens,
            valorBrutoCentavos = totais.valorBruto,
            descontoCentavos = totais.desconto,
            valorAPagarCentavos = totais.valorAPagar,
            pagamentos = totais.pagamentos,
        )
    }

    private fun parseEmitente(doc: Document): Emitente {
        val bloco = doc.selectFirst("div.txtCenter") ?: throw NfceParseException("Emitente não encontrado")
        val razao = bloco.getElementById("u20")?.text()?.trim().orEmpty()
        val linhas = bloco.select("div.text").map { it.text() }
        val cnpj = linhas.firstOrNull { it.contains("CNPJ") }?.filter { it.isDigit() }.orEmpty()
        val endereco = linhas.firstOrNull { !it.contains("CNPJ") }
            ?.split(',')?.map { it.trim() }?.filter { it.isNotEmpty() }?.joinToString(", ")
            .orEmpty()
        return Emitente(razaoSocial = razao, cnpj = cnpj, endereco = endereco)
    }

    private fun parseItem(ordem: Int, tr: Element): ItemImportado {
        fun campo(classe: String): String =
            tr.selectFirst("span.$classe")?.ownText()?.trim()
                ?: throw NfceParseException("Item $ordem sem o campo $classe")

        val descricao = tr.selectFirst("span.txtTit")?.text()?.trim()
            ?: throw NfceParseException("Item $ordem sem descrição")
        val codigo = tr.selectFirst("span.RCod")?.text()?.let {
            Regex("""Código:\s*([^)]*)""").find(it)?.groupValues?.get(1)?.trim()
        }.orEmpty()

        return ItemImportado(
            ordem = ordem,
            codigo = codigo,
            descricao = descricao,
            quantidade = parseDecimalBr(campo("Rqtd"))
                ?: throw NfceParseException("Item $ordem: quantidade inválida"),
            unidade = campo("RUN"),
            valorUnitario = parseDecimalBr(campo("RvlUnit"))
                ?: throw NfceParseException("Item $ordem: valor unitário inválido"),
            valorTotalCentavos = tr.selectFirst("span.valor")?.text()?.let(::parseCentavosBr)
                ?: throw NfceParseException("Item $ordem: valor total inválido"),
        )
    }

    private class Totais(
        val valorBruto: Long?,
        val desconto: Long,
        val valorAPagar: Long,
        val pagamentos: List<Pagamento>,
    )

    /**
     * O bloco #totalNota é uma sequência de linhas "rótulo: valor".
     * As linhas após "Forma de pagamento" são os meios de pagamento, até "Troco" ou tributos.
     */
    private fun parseTotais(doc: Document): Totais {
        val bloco = doc.getElementById("totalNota") ?: throw NfceParseException("Totais não encontrados")

        var valorBruto: Long? = null
        var desconto = 0L
        var valorAPagar: Long? = null
        val pagamentos = mutableListOf<Pagamento>()
        var lendoPagamentos = false

        for (linha in bloco.children()) {
            val rotulo = linha.selectFirst("label")?.text()?.trim().orEmpty()
            val valor = linha.selectFirst("span.totalNumb")?.text().orEmpty()
            val r = rotulo.lowercase()
            when {
                linha.id() == "linhaForma" -> lendoPagamentos = true
                r.startsWith("troco") || r.startsWith("informação dos tributos") -> lendoPagamentos = false
                lendoPagamentos -> parseCentavosBr(valor)?.let { pagamentos += Pagamento(rotulo, it) }
                r.startsWith("valor total") -> valorBruto = parseCentavosBr(valor)
                r.startsWith("descontos") -> desconto = parseCentavosBr(valor) ?: 0L
                r.startsWith("valor a pagar") -> valorAPagar = parseCentavosBr(valor)
            }
        }

        return Totais(
            valorBruto = valorBruto,
            desconto = desconto,
            valorAPagar = valorAPagar ?: throw NfceParseException("Valor a pagar não encontrado"),
            pagamentos = pagamentos,
        )
    }
}
