package dev.lucasgola.financas.nfce

import java.math.BigDecimal
import java.time.LocalDateTime

/** Resultado do parse da página de consulta da NFC-e, antes de ser persistido. */
data class NotaImportada(
    val chave: ChaveAcesso,
    val emitente: Emitente,
    val numero: Long,
    val serie: Int,
    val dataEmissao: LocalDateTime,
    val itens: List<ItemImportado>,
    /** Soma bruta dos itens, quando a SEFAZ exibe "Valor total" separado (há desconto). */
    val valorBrutoCentavos: Long?,
    val descontoCentavos: Long,
    /** "Valor a pagar": o que de fato saiu do bolso. */
    val valorAPagarCentavos: Long,
    val pagamentos: List<Pagamento>,
) {
    /** Soma dos itens − desconto deve bater com o valor a pagar. Divergência é exibida como alerta, não bloqueia. */
    val totaisConferem: Boolean
        get() = itens.sumOf { it.valorTotalCentavos } - descontoCentavos == valorAPagarCentavos
}

data class Emitente(
    val razaoSocial: String,
    /** Só dígitos. */
    val cnpj: String,
    val endereco: String,
)

data class ItemImportado(
    val ordem: Int,
    val codigo: String,
    val descricao: String,
    val quantidade: BigDecimal,
    val unidade: String,
    val valorUnitario: BigDecimal,
    val valorTotalCentavos: Long,
)

data class Pagamento(
    val forma: String,
    val valorCentavos: Long,
)
