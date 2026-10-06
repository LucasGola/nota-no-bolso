package dev.lucasgola.financas.filtro

import dev.lucasgola.financas.data.OrigemLancamento
import dev.lucasgola.financas.data.TipoLancamento
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.sql.Connection
import java.sql.DriverManager
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth

class FiltroTest {

    private val hoje = LocalDate.of(2026, 10, 6)

    // ---- Períodos ----

    @Test
    fun `mes cobre do dia 1 ao ultimo dia, no fuso de SP`() {
        val (inicio, fim) = Periodo.Mes(YearMonth.of(2026, 10)).intervalo(hoje).emInstants()
        assertEquals(Instant.parse("2026-10-01T03:00:00Z"), inicio)
        assertEquals(Instant.parse("2026-11-01T03:00:00Z"), fim)
    }

    @Test
    fun `ultimos 30 dias inclui hoje`() {
        val i = Periodo.UltimosDias(30).intervalo(hoje)
        assertEquals(LocalDate.of(2026, 9, 7), i.inicio)
        assertEquals(hoje, i.fim)
    }

    @Test
    fun `tudo nao tem intervalo`() {
        assertNull(Periodo.Tudo.intervalo(hoje))
    }

    @Test
    fun `rotulo do mes omite o ano corrente`() {
        assertEquals("Outubro", Periodo.Mes(YearMonth.of(2026, 10)).rotulo(hoje))
        assertEquals("Dezembro 2025", Periodo.Mes(YearMonth.of(2025, 12)).rotulo(hoje))
    }

    @Test
    fun `limpar restricoes mantem o periodo`() {
        val f = Filtro(periodo = Periodo.Ano(2025), tipo = TipoLancamento.SAIDA, busca = "x", categorias = setOf(1))
        assertTrue(f.temRestricoes)
        val limpo = f.limparRestricoes()
        assertFalse(limpo.temRestricoes)
        assertEquals(Periodo.Ano(2025), limpo.periodo)
    }

    @Test
    fun `escapa curingas do LIKE`() {
        assertEquals("50\\%", ConsultaLancamentos.escaparLike("50%"))
        assertEquals("a\\_b", ConsultaLancamentos.escaparLike("a_b"))
    }

    // ---- SQL executado num SQLite real ----

    private lateinit var conn: Connection

    @Before
    fun criarBanco() {
        conn = DriverManager.getConnection("jdbc:sqlite::memory:")
        conn.createStatement().use { st ->
            // Só as colunas que a consulta usa, com os mesmos nomes e tipos do Room.
            st.executeUpdate("CREATE TABLE categoria (id INTEGER PRIMARY KEY, nome TEXT, cor INTEGER)")
            st.executeUpdate(
                """CREATE TABLE lancamento (id INTEGER PRIMARY KEY, tipo TEXT, valorCentavos INTEGER, dataHora INTEGER,
                   descricao TEXT, categoriaId INTEGER, origem TEXT, estabelecimentoId INTEGER)"""
            )
            st.executeUpdate("CREATE TABLE nota_fiscal (id INTEGER PRIMARY KEY, lancamentoId INTEGER)")
            st.executeUpdate("CREATE TABLE item_nota (id INTEGER PRIMARY KEY, notaId INTEGER, descricao TEXT)")
            st.executeUpdate("INSERT INTO categoria VALUES (1, 'Mercado', 0), (2, 'Salário', 0), (3, 'Lazer', 0)")
        }
        // id, tipo, valor, data (local SP), descrição, categoria, origem, estabelecimento
        lancar(1, "SAIDA", 8490, "2026-10-02T12:08", "Horizonte Restaurantes", 3, "NFCE", 10)
        lancar(2, "ENTRADA", 500000, "2026-10-05T12:00", "Salário outubro", 2, "MANUAL", null)
        lancar(3, "SAIDA", 25000, "2026-09-30T23:30", "Mercado do mês", 1, "MANUAL", null) // 30/09 23:30 em SP
        lancar(4, "SAIDA", 1200, "2026-10-31T23:59", "Desconto 50% padaria", 1, "MANUAL", null)
        conn.createStatement().use { st ->
            st.executeUpdate("INSERT INTO nota_fiscal VALUES (100, 1)")
            st.executeUpdate("INSERT INTO item_nota VALUES (1, 100, 'Crunch Chicken'), (2, 100, 'Coca Zero Lata')")
        }
    }

    @After
    fun fecharBanco() = conn.close()

    private fun lancar(id: Long, tipo: String, valor: Long, dataLocal: String, desc: String, cat: Long, origem: String, est: Long?) {
        val instante = java.time.LocalDateTime.parse(dataLocal).atZone(dev.lucasgola.financas.util.ZONA).toInstant()
        conn.prepareStatement("INSERT INTO lancamento VALUES (?, ?, ?, ?, ?, ?, ?, ?)").use { ps ->
            ps.setLong(1, id); ps.setString(2, tipo); ps.setLong(3, valor); ps.setLong(4, instante.toEpochMilli())
            ps.setString(5, desc); ps.setLong(6, cat); ps.setString(7, origem)
            if (est == null) ps.setNull(8, java.sql.Types.INTEGER) else ps.setLong(8, est)
            ps.executeUpdate()
        }
    }

    private fun ids(filtro: Filtro): List<Long> {
        val q = ConsultaLancamentos.montar(filtro, hoje)
        return conn.prepareStatement(q.sql).use { ps ->
            q.args.forEachIndexed { i, a -> ps.setObject(i + 1, a) }
            ps.executeQuery().use { rs -> buildList { while (rs.next()) add(rs.getLong("id")) } }
        }
    }

    private val outubro = Filtro(periodo = Periodo.Mes(YearMonth.of(2026, 10)))

    @Test
    fun `periodo respeita o fuso e ordena do mais recente`() {
        // O lançamento 3 (30/09 23:30 em SP = 01/10 02:30 UTC) não pode entrar em outubro.
        assertEquals(listOf(4L, 2L, 1L), ids(outubro))
        assertEquals(listOf(4L, 2L, 1L, 3L), ids(Filtro(periodo = Periodo.Tudo)))
    }

    @Test
    fun `filtra por tipo, categoria, origem e estabelecimento`() {
        assertEquals(listOf(2L), ids(outubro.copy(tipo = TipoLancamento.ENTRADA)))
        assertEquals(listOf(4L), ids(outubro.copy(categorias = setOf(1))))
        assertEquals(listOf(4L, 1L), ids(outubro.copy(categorias = setOf(1, 3))))
        assertEquals(listOf(1L), ids(outubro.copy(origem = OrigemLancamento.NFCE)))
        assertEquals(listOf(1L), ids(outubro.copy(estabelecimentos = setOf(10))))
    }

    @Test
    fun `filtra por faixa de valor`() {
        assertEquals(listOf(1L), ids(outubro.copy(valorMinCentavos = 5000, valorMaxCentavos = 10000)))
    }

    @Test
    fun `busca encontra pela descricao e pelos itens da nota, sem diferenciar maiusculas`() {
        assertEquals(listOf(1L), ids(outubro.copy(busca = "coca")))
        assertEquals(listOf(2L), ids(outubro.copy(busca = "SALÁRIO".lowercase())))
        assertEquals(listOf(1L), ids(outubro.copy(busca = "horizonte")))
    }

    @Test
    fun `porcentagem na busca e literal`() {
        assertEquals(listOf(4L), ids(Filtro(periodo = Periodo.Tudo, busca = "50%")))
        assertEquals(emptyList<Long>(), ids(Filtro(periodo = Periodo.Tudo, busca = "%x")))
    }
}
