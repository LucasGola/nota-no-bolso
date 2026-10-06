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
