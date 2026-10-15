package dev.lucasgola.financas.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Embedded
import androidx.room.Insert
import androidx.room.Query
import androidx.room.RawQuery
import androidx.room.Update
import androidx.sqlite.db.SupportSQLiteQuery
import dev.lucasgola.financas.filtro.ItemRateio
import kotlinx.coroutines.flow.Flow

private const val SQL_ITENS_RATEIO = """
    SELECT n.lancamentoId AS lancamentoId, i.categoriaId AS categoriaId,
           i.valorTotalCentavos - i.descontoCentavos AS valorCentavos
    FROM item_nota i JOIN nota_fiscal n ON n.id = i.notaId
    WHERE n.lancamentoId IN (
        SELECT n2.lancamentoId FROM nota_fiscal n2 JOIN item_nota i2 ON i2.notaId = n2.id
        WHERE i2.categoriaId IS NOT NULL
    )
"""

@Dao
interface CategoriaDao {
    @Query("SELECT * FROM categoria ORDER BY ativa DESC, nome COLLATE NOCASE")
    fun observarTodas(): Flow<List<Categoria>>

    @Query("SELECT * FROM categoria")
    suspend fun listarTodas(): List<Categoria>

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
    /** Consulta montada por [dev.lucasgola.financas.filtro.ConsultaLancamentos]. */
    @RawQuery(observedEntities = [Lancamento::class, Categoria::class, NotaFiscal::class, ItemNota::class])
    fun observarFiltrado(consulta: SupportSQLiteQuery): Flow<List<LancamentoComCategoria>>

    /** Versão pontual da consulta filtrada (exportação). */
    @RawQuery
    suspend fun listarFiltrado(consulta: SupportSQLiteQuery): List<LancamentoComCategoria>

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
interface MetodoPagamentoDao {
    @Query("SELECT * FROM metodo_pagamento ORDER BY ativo DESC, nome COLLATE NOCASE")
    fun observarTodos(): Flow<List<MetodoPagamento>>

    @Query("SELECT * FROM metodo_pagamento")
    suspend fun listarTodos(): List<MetodoPagamento>

    @Insert
    suspend fun inserir(metodo: MetodoPagamento): Long

    @Update
    suspend fun atualizar(metodo: MetodoPagamento)

    @Delete
    suspend fun excluir(metodo: MetodoPagamento)

    @Query("SELECT COUNT(*) FROM lancamento WHERE metodoPagamentoId = :id")
    suspend fun contarLancamentos(id: Long): Int
}

@Dao
interface EstabelecimentoDao {
    @Query("SELECT * FROM estabelecimento ORDER BY razaoSocial COLLATE NOCASE")
    fun observarTodos(): Flow<List<Estabelecimento>>

    @Query("SELECT * FROM estabelecimento")
    suspend fun listarTodos(): List<Estabelecimento>

    @Query("SELECT * FROM estabelecimento WHERE id = :id")
    suspend fun buscar(id: Long): Estabelecimento?

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

    @Query("SELECT * FROM nota_fiscal WHERE lancamentoId IN (:lancamentoIds)")
    suspend fun notasDosLancamentos(lancamentoIds: List<Long>): List<NotaFiscal>

    @Query("SELECT * FROM item_nota WHERE notaId IN (:notaIds) ORDER BY notaId, ordem")
    suspend fun itensDasNotas(notaIds: List<Long>): List<ItemNota>

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

    @Query("UPDATE item_nota SET categoriaId = :categoriaId WHERE id = :itemId")
    suspend fun classificarItem(itemId: Long, categoriaId: Long?)

    /** Todos os itens das notas que têm ao menos um item classificado (são as únicas que precisam de rateio). */
    @Query(SQL_ITENS_RATEIO)
    fun observarItensRateio(): Flow<List<ItemRateio>>

    @Query(SQL_ITENS_RATEIO)
    suspend fun listarItensRateio(): List<ItemRateio>
}

/** Leitura e escrita em massa de todas as tabelas, usada só pelo backup. */
@Dao
interface BackupDao {
    @Query("SELECT * FROM categoria ORDER BY id") suspend fun categorias(): List<Categoria>
    @Query("SELECT * FROM metodo_pagamento ORDER BY id") suspend fun metodos(): List<MetodoPagamento>
    @Query("SELECT * FROM estabelecimento ORDER BY id") suspend fun estabelecimentos(): List<Estabelecimento>
    @Query("SELECT * FROM lancamento ORDER BY id") suspend fun lancamentos(): List<Lancamento>
    @Query("SELECT * FROM nota_fiscal ORDER BY id") suspend fun notas(): List<NotaFiscal>
    @Query("SELECT * FROM item_nota ORDER BY id") suspend fun itens(): List<ItemNota>

    @Insert suspend fun inserirCategorias(v: List<Categoria>)
    @Insert suspend fun inserirMetodos(v: List<MetodoPagamento>)
    @Insert suspend fun inserirEstabelecimentos(v: List<Estabelecimento>)
    @Insert suspend fun inserirLancamentos(v: List<Lancamento>)
    @Insert suspend fun inserirNotas(v: List<NotaFiscal>)
    @Insert suspend fun inserirItens(v: List<ItemNota>)

    // Ordem de filhos para pais, por causa das chaves estrangeiras.
    @Query("DELETE FROM item_nota") suspend fun limparItens()
    @Query("DELETE FROM nota_fiscal") suspend fun limparNotas()
    @Query("DELETE FROM lancamento") suspend fun limparLancamentos()
    @Query("DELETE FROM estabelecimento") suspend fun limparEstabelecimentos()
    @Query("DELETE FROM categoria") suspend fun limparCategorias()
    @Query("DELETE FROM metodo_pagamento") suspend fun limparMetodos()
}
