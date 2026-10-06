package dev.lucasgola.financas.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Embedded
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface CategoriaDao {
    @Query("SELECT * FROM categoria ORDER BY ativa DESC, nome COLLATE NOCASE")
    fun observarTodas(): Flow<List<Categoria>>

    @Insert
    suspend fun inserir(categoria: Categoria): Long

    @Update
    suspend fun atualizar(categoria: Categoria)

    @Delete
    suspend fun excluir(categoria: Categoria)

    /** Categoria usada quando não há escolha do usuário (reprocesso em lote): "Outros", se existir. */
    @Query(
        """
        SELECT * FROM categoria WHERE ativa = 1 AND tipo IN ('SAIDA', 'AMBOS')
        ORDER BY (nome = 'Outros') DESC, id LIMIT 1
        """
    )
    suspend fun categoriaPadraoSaida(): Categoria?

    @Query("SELECT COUNT(*) FROM lancamento WHERE categoriaId = :id")
    suspend fun contarLancamentos(id: Long): Int
}

data class LancamentoComCategoria(
    @Embedded val lancamento: Lancamento,
    val categoriaNome: String,
    val categoriaCor: Long,
)

@Dao
interface LancamentoDao {
    @Query(
        """
        SELECT l.*, c.nome AS categoriaNome, c.cor AS categoriaCor
        FROM lancamento l JOIN categoria c ON c.id = l.categoriaId
        ORDER BY l.dataHora DESC, l.id DESC
        """
    )
    fun observarExtrato(): Flow<List<LancamentoComCategoria>>

    @Query("SELECT * FROM lancamento WHERE id = :id")
    suspend fun buscar(id: Long): Lancamento?

    @Insert
    suspend fun inserir(lancamento: Lancamento): Long

    @Update
    suspend fun atualizar(lancamento: Lancamento)

    @Delete
    suspend fun excluir(lancamento: Lancamento)
}

@Dao
interface EstabelecimentoDao {
    @Query("SELECT * FROM estabelecimento WHERE cnpj = :cnpj")
    suspend fun buscarPorCnpj(cnpj: String): Estabelecimento?

    @Insert
    suspend fun inserir(estabelecimento: Estabelecimento): Long

    @Update
    suspend fun atualizar(estabelecimento: Estabelecimento)
}

@Dao
interface NotaDao {
    @Query("SELECT * FROM nota_fiscal WHERE chaveAcesso = :chave")
    suspend fun buscarPorChave(chave: String): NotaFiscal?

    @Query("SELECT * FROM nota_fiscal WHERE id = :id")
    suspend fun buscar(id: Long): NotaFiscal?

    @Query("SELECT * FROM nota_fiscal WHERE lancamentoId = :lancamentoId")
    suspend fun buscarPorLancamento(lancamentoId: Long): NotaFiscal?

    @Query("SELECT * FROM nota_fiscal WHERE status = 'PENDENTE' ORDER BY importadaEm DESC")
    fun observarPendentes(): Flow<List<NotaFiscal>>

    @Query("SELECT COUNT(*) FROM nota_fiscal WHERE status = 'PENDENTE'")
    fun contarPendentes(): Flow<Int>

    @Query("SELECT * FROM item_nota WHERE notaId = :notaId ORDER BY ordem")
    suspend fun itens(notaId: Long): List<ItemNota>

    @Insert
    suspend fun inserir(nota: NotaFiscal): Long

    @Update
    suspend fun atualizar(nota: NotaFiscal)

    @Delete
    suspend fun excluir(nota: NotaFiscal)

    @Insert
    suspend fun inserirItens(itens: List<ItemNota>)

    @Query("DELETE FROM item_nota WHERE notaId = :notaId")
    suspend fun excluirItens(notaId: Long)
}
