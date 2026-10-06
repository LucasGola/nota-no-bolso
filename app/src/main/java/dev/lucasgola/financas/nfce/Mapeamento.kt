package dev.lucasgola.financas.nfce

import dev.lucasgola.financas.data.ItemNota
import dev.lucasgola.financas.data.Lancamento
import dev.lucasgola.financas.data.NotaFiscal
import dev.lucasgola.financas.data.OrigemLancamento
import dev.lucasgola.financas.data.StatusNota
import dev.lucasgola.financas.data.TipoLancamento
import dev.lucasgola.financas.util.ZONA
import java.time.Instant

// Conversões puras entre o resultado do parser e as entidades do banco (sem I/O, testáveis na JVM).

val NotaImportada.dataEmissaoInstant: Instant get() = dataEmissao.atZone(ZONA).toInstant()

/** "Outros" · "Cartão de Crédito, Dinheiro" — formas distintas, na ordem da nota. */
fun NotaImportada.resumoPagamento(): String? =
    pagamentos.map { it.forma }.distinct().joinToString(", ").ifEmpty { null }

fun NotaImportada.paraLancamento(
    categoriaId: Long,
    descricao: String,
    dataHora: Instant,
    estabelecimentoId: Long,
): Lancamento = Lancamento(
    tipo = TipoLancamento.SAIDA,
    valorCentavos = valorAPagarCentavos,
    dataHora = dataHora,
    descricao = descricao,
    categoriaId = categoriaId,
    origem = OrigemLancamento.NFCE,
    formaPagamento = resumoPagamento(),
    estabelecimentoId = estabelecimentoId,
)

fun NotaImportada.paraNotaFiscal(urlQr: String, lancamentoId: Long, id: Long = 0): NotaFiscal = NotaFiscal(
    id = id,
    chaveAcesso = chave.valor,
    urlQr = urlQr,
    uf = chave.uf,
    numero = numero,
    serie = serie,
    dataEmissao = dataEmissaoInstant,
    valorBrutoCentavos = valorBrutoCentavos,
    descontoCentavos = descontoCentavos,
    valorTotalCentavos = valorAPagarCentavos,
    status = StatusNota.IMPORTADA,
    lancamentoId = lancamentoId,
)

fun NotaImportada.paraItens(notaId: Long): List<ItemNota> = itens.map {
    ItemNota(
        notaId = notaId,
        ordem = it.ordem,
        codigo = it.codigo,
        descricao = it.descricao,
        quantidade = it.quantidade,
        unidade = it.unidade,
        valorUnitario = it.valorUnitario,
        valorTotalCentavos = it.valorTotalCentavos,
    )
}

/** Nota que não pôde ser consultada: guarda só o que a chave revela. */
fun notaPendente(chave: ChaveAcesso, urlQr: String, erro: String, id: Long = 0): NotaFiscal = NotaFiscal(
    id = id,
    chaveAcesso = chave.valor,
    urlQr = urlQr,
    uf = chave.uf,
    numero = chave.numero,
    serie = chave.serie,
    dataEmissao = null,
    valorBrutoCentavos = null,
    descontoCentavos = 0,
    valorTotalCentavos = null,
    status = StatusNota.PENDENTE,
    erroMsg = erro,
)
