package dev.lucasgola.financas.export

import dev.lucasgola.financas.data.OrigemLancamento
import dev.lucasgola.financas.data.TipoLancamento
import dev.lucasgola.financas.util.ZONA
import dev.lucasgola.financas.util.formatarCnpj
import java.math.BigDecimal
import java.time.format.DateTimeFormatter

/**
 * CSV no formato que o Excel em português abre direto: separador ";", vírgula decimal,
 * UTF-8 com BOM e campos entre aspas quando necessário (RFC 4180).
 *
 * O valor do lançamento sai com sinal (saída negativa), para somar a coluna e obter o saldo.
 * Com [incluirItens], cada item de nota vira uma linha que repete os dados do lançamento:
 * nesse modo, some a coluna "Valor do item", não a "Valor".
 */
object Csv {
    const val MIME = "text/csv"
    private const val BOM = "\uFEFF"
    private const val SEP = ';'
    private val fmtData = DateTimeFormatter.ofPattern("dd/MM/yyyy")
    private val fmtHora = DateTimeFormatter.ofPattern("HH:mm")

    private val colunas = listOf(
        "Data", "Hora", "Tipo", "Descrição", "Categoria", "Estabelecimento", "CNPJ",
        "Forma de pagamento", "Origem", "Valor", "Observação",
    )
    private val colunasItem = listOf("Item", "Código", "Quantidade", "Unidade", "Valor unitário", "Valor do item")

    fun gerar(dados: DadosExportacao, incluirItens: Boolean): String = buildString {
        append(BOM)
        linha(if (incluirItens) colunas + colunasItem else colunas)
        for (l in dados.lancamentos) {
            val base = camposLancamento(l)
            if (!incluirItens) {
                linha(base)
            } else if (l.itens.isEmpty()) {
                linha(base + List(colunasItem.size) { "" })
            } else {
                l.itens.forEach { i ->
                    linha(
                        base + listOf(
                            texto(i.descricao), texto(i.codigo), decimal(i.quantidade), texto(i.unidade),
                            decimal(i.valorUnitario), centavos(i.valorTotalCentavos),
                        )
                    )
                }
            }
        }
    }

    private fun camposLancamento(e: LancamentoExportado): List<String> {
        val l = e.dados.lancamento
        val quando = l.dataHora.atZone(ZONA)
        val sinal = if (l.tipo == TipoLancamento.SAIDA) -1 else 1
        return listOf(
            quando.format(fmtData),
            quando.format(fmtHora),
            if (l.tipo == TipoLancamento.ENTRADA) "Entrada" else "Saída",
            texto(l.descricao),
            texto(e.dados.categoriaNome),
            texto(e.estabelecimento?.razaoSocial.orEmpty()),
            e.estabelecimento?.cnpj?.let(::formatarCnpj).orEmpty(),
            texto(l.formaPagamento.orEmpty()),
            if (l.origem == OrigemLancamento.NFCE) "Nota fiscal" else "Manual",
            centavos(sinal * l.valorCentavos),
            texto(l.observacao.orEmpty()),
        )
    }

    /**
     * Campo de texto vindo de fora (SEFAZ, digitação): se começar como fórmula (=, +, -, @),
     * o Excel executaria. Um apóstrofo na frente faz a planilha tratar como texto.
     */
    internal fun texto(v: String): String =
        if (v.isNotEmpty() && v[0] in "=+-@\t\r") "'$v" else v

    /** 1234,56 — sem separador de milhar, para o Excel reconhecer como número. */
    private fun centavos(v: Long): String = BigDecimal.valueOf(v, 2).toPlainString().replace('.', ',')

    private fun decimal(v: BigDecimal): String = v.stripTrailingZeros().toPlainString().replace('.', ',')

    private fun StringBuilder.linha(campos: List<String>) {
        campos.joinTo(this, SEP.toString()) { escapar(it) }
        append("\r\n")
    }

    internal fun escapar(campo: String): String =
        if (campo.any { it == SEP || it == '"' || it == '\n' || it == '\r' }) "\"" + campo.replace("\"", "\"\"") + "\""
        else campo
}
