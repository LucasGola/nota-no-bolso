package dev.lucasgola.financas.data

import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.sql.Connection
import java.sql.DriverManager

/**
 * Confere as migrações num SQLite real: um banco criado pelo schema antigo, com dados, é migrado
 * e comparado com um banco criado do zero pelo schema novo (colunas, chaves estrangeiras e índices,
 * o mesmo que o Room valida ao abrir o banco). Os schemas são os JSON exportados pelo Room.
 */
class MigracaoTest {

    private val abertas = mutableListOf<Connection>()

    @After
    fun fechar() = abertas.forEach { it.close() }

    private fun schema(versao: Int): JSONObject {
        val arquivo = File("schemas/dev.lucasgola.financas.data.AppDatabase/$versao.json")
        return JSONObject(arquivo.readText()).getJSONObject("database")
    }

    /** Banco vazio com as tabelas e índices do schema, com chaves estrangeiras ligadas como no Room. */
    private fun criar(versao: Int): Connection {
        val conn = DriverManager.getConnection("jdbc:sqlite::memory:").also { abertas += it }
        conn.createStatement().use { st ->
            st.execute("PRAGMA foreign_keys = ON")
            val entidades = schema(versao).getJSONArray("entities")
            for (i in 0 until entidades.length()) {
                val e = entidades.getJSONObject(i)
                val tabela = e.getString("tableName")
                st.execute(e.getString("createSql").replace("\${TABLE_NAME}", tabela))
                val indices = e.optJSONArray("indices") ?: continue
                for (j in 0 until indices.length()) {
                    st.execute(indices.getJSONObject(j).getString("createSql").replace("\${TABLE_NAME}", tabela))
                }
            }
        }
        return conn
    }

    private fun Connection.exec(sql: String) = createStatement().use { it.execute(sql) }

    private fun Connection.linhas(sql: String): List<List<Any?>> = createStatement().use { st ->
        st.executeQuery(sql).use { rs ->
            val n = rs.metaData.columnCount
            buildList { while (rs.next()) add((1..n).map { rs.getObject(it) }) }
        }
    }

    /** Estrutura de uma tabela, independente da ordem: colunas, chaves estrangeiras e índices. */
    private fun Connection.estrutura(tabela: String): Map<String, Any> {
        val colunas = linhas("PRAGMA table_info(`$tabela`)")
            .map { listOf(it[1], it[2], it[3], it[4], it[5]) } // nome, tipo, notnull, default, pk
            .sortedBy { it[0].toString() }
        val chaves = linhas("PRAGMA foreign_key_list(`$tabela`)")
            .map { listOf(it[2], it[3], it[4], it[5], it[6]) } // tabela, de, para, on_update, on_delete
            .sortedBy { it.toString() }
        val indices = linhas("PRAGMA index_list(`$tabela`)")
            .filter { !it[1].toString().startsWith("sqlite_autoindex") }
            .map { idx ->
                val nome = idx[1].toString()
                listOf(nome, idx[2], linhas("PRAGMA index_info(`$nome`)").map { it[2] })
            }
            .sortedBy { it[0].toString() }
        return mapOf("colunas" to colunas, "chaves" to chaves, "indices" to indices)
    }

    private fun Connection.tabelas(): List<String> =
        linhas("SELECT name FROM sqlite_master WHERE type = 'table' AND name NOT LIKE 'sqlite_%' ORDER BY name")
            .map { it[0].toString() }

    @Test
    fun `migracao 1 para 2 chega no schema 2 e preserva os dados`() {
        val antigo = criar(1)
        antigo.exec("INSERT INTO categoria VALUES (1, 'Mercado', 4281236786, 'SAIDA', 1)")
        antigo.exec("INSERT INTO estabelecimento VALUES (1, '58891504001796', 'Horizonte', 'Rua X', 1)")
        antigo.exec(
            "INSERT INTO lancamento VALUES (10, 'SAIDA', 8490, 1759417680000, 'Almoço', 1, 'NFCE', 'Débito', 1, NULL, 1, 2)"
        )
        antigo.exec(
            "INSERT INTO nota_fiscal VALUES (20, '35261058891504001796650020000075601259531450', 'u', 'SP', 7560, 2, " +
                "1759417680000, 8490, 0, 8490, 'IMPORTADA', NULL, 10, 3)"
        )
        antigo.exec("INSERT INTO item_nota VALUES (30, 20, 1, '26158', 'Crunch', '4', 'UN', '10.10', 0, 4040, NULL)")

        Migracoes.SQL_1_PARA_2.forEach { antigo.exec(it) }

        val novo = criar(2)
        assertEquals(novo.tabelas(), antigo.tabelas())
        for (tabela in novo.tabelas()) {
            assertEquals("estrutura de $tabela", novo.estrutura(tabela), antigo.estrutura(tabela))
        }

        // Dados intactos, e o lançamento antigo fica sem método.
        assertEquals(
            listOf(listOf<Any?>(10, 8490, "Débito", null)),
            antigo.linhas("SELECT id, valorCentavos, formaPagamento, metodoPagamentoId FROM lancamento"),
        )
        assertEquals(listOf(listOf<Any?>(1)), antigo.linhas("SELECT COUNT(*) FROM item_nota"))
        assertTrue(antigo.linhas("PRAGMA foreign_key_check").isEmpty())

        // A coluna nova funciona: vincula, e apagar o método deixa o lançamento sem método.
        antigo.exec("INSERT INTO metodo_pagamento VALUES (1, 'VR', 'Vale-refeição', 1)")
        antigo.exec("UPDATE lancamento SET metodoPagamentoId = 1 WHERE id = 10")
        antigo.exec("DELETE FROM metodo_pagamento WHERE id = 1")
        assertEquals(listOf(listOf<Any?>(null)), antigo.linhas("SELECT metodoPagamentoId FROM lancamento"))
    }

    private fun assertMesmaEstrutura(esperado: Connection, migrado: Connection) {
        assertEquals(esperado.tabelas(), migrado.tabelas())
        for (tabela in esperado.tabelas()) {
            assertEquals("estrutura de $tabela", esperado.estrutura(tabela), migrado.estrutura(tabela))
        }
        assertTrue(migrado.linhas("PRAGMA foreign_key_check").isEmpty())
    }

    @Test
    fun `migracao 2 para 3 chega no schema 3 e preserva os dados`() {
        val antigo = criar(2)
        antigo.exec("INSERT INTO categoria VALUES (1, 'Mercado', 4281236786, 'SAIDA', 1)")
        antigo.exec("INSERT INTO metodo_pagamento VALUES (1, 'VR', NULL, 1)")
        antigo.exec("INSERT INTO estabelecimento VALUES (1, '58891504001796', 'Horizonte', 'Rua X', 1)")
        antigo.exec(
            "INSERT INTO lancamento VALUES (10, 'SAIDA', 8490, 1759417680000, 'Almoço', 1, 'NFCE', 'Débito', 1, NULL, 1, 2, 1)"
        )

        Migracoes.SQL_2_PARA_3.forEach { antigo.exec(it) }

        assertMesmaEstrutura(criar(3), antigo)
        assertEquals(
            listOf(listOf<Any?>(1, "Horizonte", 1, null)),
            antigo.linhas("SELECT id, razaoSocial, categoriaPadraoId, metodoPagamentoPadraoId FROM estabelecimento"),
        )
        assertEquals(listOf(listOf<Any?>(10, 1)), antigo.linhas("SELECT id, metodoPagamentoId FROM lancamento"))

        // Apagar o método limpa o padrão do estabelecimento, sem apagar o estabelecimento.
        antigo.exec("UPDATE estabelecimento SET metodoPagamentoPadraoId = 1")
        antigo.exec("UPDATE lancamento SET metodoPagamentoId = NULL")
        antigo.exec("DELETE FROM metodo_pagamento WHERE id = 1")
        assertEquals(listOf(listOf<Any?>(null)), antigo.linhas("SELECT metodoPagamentoPadraoId FROM estabelecimento"))
    }

    @Test
    fun `quem pula versoes migra de 1 direto para 3`() {
        val antigo = criar(1)
        antigo.exec("INSERT INTO categoria VALUES (1, 'Mercado', 4281236786, 'SAIDA', 1)")
        antigo.exec("INSERT INTO estabelecimento VALUES (1, '58891504001796', 'Horizonte', 'Rua X', 1)")
        antigo.exec(
            "INSERT INTO lancamento VALUES (10, 'SAIDA', 8490, 1759417680000, 'Almoço', 1, 'NFCE', 'Débito', 1, NULL, 1, 2)"
        )

        (Migracoes.SQL_1_PARA_2 + Migracoes.SQL_2_PARA_3).forEach { antigo.exec(it) }

        assertMesmaEstrutura(criar(3), antigo)
        assertEquals(listOf(listOf<Any?>(10, 8490, null)), antigo.linhas("SELECT id, valorCentavos, metodoPagamentoId FROM lancamento"))
    }
}
