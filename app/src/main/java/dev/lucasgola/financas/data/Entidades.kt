package dev.lucasgola.financas.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.math.BigDecimal
import java.time.Instant

enum class TipoLancamento { ENTRADA, SAIDA }

enum class TipoCategoria { ENTRADA, SAIDA, AMBOS;

    fun aceita(tipo: TipoLancamento): Boolean = this == AMBOS || name == tipo.name
}

enum class OrigemLancamento { MANUAL, NFCE }

enum class StatusNota {
    /** Lida, mas a consulta à SEFAZ falhou; não entra nos totais. */
    PENDENTE,
    /** Consulta OK: tem itens e lançamento. */
    IMPORTADA,
    /** Consulta nunca deu certo; valor informado à mão, sem itens. */
    MANUAL,
}

@Entity(tableName = "categoria")
data class Categoria(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val nome: String,
    /** ARGB. */
    val cor: Long,
    val tipo: TipoCategoria,
    val ativa: Boolean = true,
)

@Entity(
    tableName = "estabelecimento",
    indices = [Index(value = ["cnpj"], unique = true), Index("categoriaPadraoId")],
    foreignKeys = [ForeignKey(
        entity = Categoria::class, parentColumns = ["id"], childColumns = ["categoriaPadraoId"],
        onDelete = ForeignKey.SET_NULL,
    )],
)
data class Estabelecimento(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val cnpj: String,
    val razaoSocial: String,
    val endereco: String,
    /** Última categoria usada para este CNPJ; pré-preenche a próxima importação. */
    val categoriaPadraoId: Long? = null,
)

@Entity(
    tableName = "lancamento",
    indices = [Index("dataHora"), Index("categoriaId"), Index("estabelecimentoId")],
    foreignKeys = [
        ForeignKey(
            entity = Categoria::class, parentColumns = ["id"], childColumns = ["categoriaId"],
            onDelete = ForeignKey.RESTRICT,
        ),
        ForeignKey(
            entity = Estabelecimento::class, parentColumns = ["id"], childColumns = ["estabelecimentoId"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
)
data class Lancamento(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val tipo: TipoLancamento,
    /** Sempre positivo; o sinal vem de [tipo]. */
    val valorCentavos: Long,
    val dataHora: Instant,
    val descricao: String,
    val categoriaId: Long,
    val origem: OrigemLancamento = OrigemLancamento.MANUAL,
    val formaPagamento: String? = null,
    val estabelecimentoId: Long? = null,
    val observacao: String? = null,
    val criadoEm: Instant = Instant.now(),
    val atualizadoEm: Instant = Instant.now(),
)

/**
 * Nota importada pelo QR Code. Fica ligada ao lançamento que gerou; excluir o lançamento
 * exclui a nota e os itens (CASCADE), permitindo reimportar a mesma chave.
 * Nota PENDENTE ainda não tem lançamento e não entra nos totais.
 */
@Entity(
    tableName = "nota_fiscal",
    indices = [Index(value = ["chaveAcesso"], unique = true), Index("lancamentoId")],
    foreignKeys = [ForeignKey(
        entity = Lancamento::class, parentColumns = ["id"], childColumns = ["lancamentoId"],
        onDelete = ForeignKey.CASCADE,
    )],
)
data class NotaFiscal(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val chaveAcesso: String,
    val urlQr: String,
    val uf: String,
    val numero: Long,
    val serie: Int,
    val dataEmissao: Instant?,
    val valorBrutoCentavos: Long?,
    val descontoCentavos: Long,
    val valorTotalCentavos: Long?,
    val status: StatusNota,
    val erroMsg: String? = null,
    val lancamentoId: Long? = null,
    val importadaEm: Instant = Instant.now(),
)

@Entity(
    tableName = "item_nota",
    indices = [Index("notaId"), Index("categoriaId")],
    foreignKeys = [
        ForeignKey(
            entity = NotaFiscal::class, parentColumns = ["id"], childColumns = ["notaId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = Categoria::class, parentColumns = ["id"], childColumns = ["categoriaId"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
)
data class ItemNota(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val notaId: Long,
    val ordem: Int,
    val codigo: String,
    val descricao: String,
    /** BigDecimal sem arredondamento: pode ser fracionária (0,432 kg). */
    val quantidade: BigDecimal,
    val unidade: String,
    /** BigDecimal sem arredondamento: pode ter mais de 2 casas (R$ 5,899/L). */
    val valorUnitario: BigDecimal,
    val descontoCentavos: Long = 0,
    val valorTotalCentavos: Long,
    val categoriaId: Long? = null,
)
