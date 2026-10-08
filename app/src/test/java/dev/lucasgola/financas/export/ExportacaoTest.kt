package dev.lucasgola.financas.export

import dev.lucasgola.financas.data.Categoria
import dev.lucasgola.financas.data.Estabelecimento
import dev.lucasgola.financas.data.ItemNota
import dev.lucasgola.financas.data.Lancamento
import dev.lucasgola.financas.data.LancamentoComCategoria
import dev.lucasgola.financas.data.OrigemLancamento
import dev.lucasgola.financas.data.TipoCategoria
import dev.lucasgola.financas.data.TipoLancamento
import dev.lucasgola.financas.filtro.Filtro
import dev.lucasgola.financas.filtro.Periodo
import dev.lucasgola.financas.filtro.descreverFiltro
import dev.lucasgola.financas.util.ZONA
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth

class ExportacaoTest {

    private val hoje = LocalDate.of(2026, 10, 8)
    private val restaurante = Estabelecimento(1, "58891504001796", "Horizonte Restaurantes S.A.", "SP", null)

    private fun lanc(
        id: Long, tipo: TipoLancamento, valor: Long, data: String, descricao: String,
        categoria: String = "Mercado", origem: OrigemLancamento = OrigemLancamento.MANUAL,
    ) = LancamentoComCategoria(
        Lancamento(
            id = id, tipo = tipo, valorCentavos = valor,
            dataHora = LocalDateTime.parse(data).atZone(ZONA).toInstant(),
            descricao = descricao, categoriaId = 1, origem = origem,
            formaPagamento = if (origem == OrigemLancamento.NFCE) "Outros" else null,
        ),
        categoriaNome = categoria, categoriaCor = 0,
    )

    private val itens = listOf(
        ItemNota(1, 1, 1, "26158", "Crunch Chicken", BigDecimal("4"), "UN", BigDecimal("10.1"), 0, 4040),
        ItemNota(2, 1, 2, "9002", "Batata Média", BigDecimal("0.432"), "KG", BigDecimal("5.899"), 0, 255),
    )

    private val dados = DadosExportacao(
        descricaoFiltro = "Outubro 2026",
        lancamentos = listOf(
            LancamentoExportado(
                lanc(1, TipoLancamento.SAIDA, 8490, "2026-10-02T12:08", "Almoço; com \"amigos\"", "Alimentação fora", OrigemLancamento.NFCE),
                restaurante, itens,
            ),
            LancamentoExportado(lanc(2, TipoLancamento.ENTRADA, 650000, "2026-10-05T12:00", "Salário"), null, emptyList()),
        ),
    )

    // ---- CSV ----

    @Test
    fun `csv tem BOM, separador ponto e virgula, CRLF e valores com sinal`() {
        val csv = Csv.gerar(dados, incluirItens = false)
        assertTrue(csv.startsWith("\uFEFFData;Hora;Tipo;"))
        val linhas = csv.removePrefix("\uFEFF").split("\r\n").filter { it.isNotEmpty() }
        assertEquals(3, linhas.size)
        assertEquals(
            "02/10/2026;12:08;Saída;\"Almoço; com \"\"amigos\"\"\";Alimentação fora;Horizonte Restaurantes S.A.;" +
                "58.891.504/0017-96;Outros;Nota fiscal;-84,90;",
            linhas[1],
        )
        assertEquals("05/10/2026;12:00;Entrada;Salário;Mercado;;;;Manual;6500,00;", linhas[2])
    }

    @Test
    fun `csv com itens repete o lancamento e preserva casas decimais`() {
        val linhas = Csv.gerar(dados, incluirItens = true).removePrefix("\uFEFF").split("\r\n").filter { it.isNotEmpty() }
        assertEquals(4, linhas.size) // cabeçalho + 2 itens + salário sem itens
        assertTrue(linhas[0].endsWith(";Item;Código;Quantidade;Unidade;Valor unitário;Valor do item"))
        assertTrue(linhas[1].endsWith(";Crunch Chicken;26158;4;UN;10,1;40,40"))
        assertTrue(linhas[2].endsWith(";Batata Média;9002;0,432;KG;5,899;2,55"))
        assertTrue(linhas[3].endsWith(";Manual;6500,00;;;;;;;"))
    }

    @Test
    fun `csv neutraliza texto que pareceria formula`() {
        assertEquals("'=HYPERLINK(\"x\")", Csv.texto("=HYPERLINK(\"x\")"))
        assertEquals("'+55 11", Csv.texto("+55 11"))
        assertEquals("'@soma", Csv.texto("@soma"))
        assertEquals("Leite", Csv.texto("Leite"))
        assertEquals("", Csv.texto(""))
    }

    // ---- Descrição do filtro e nome do arquivo ----

    @Test
    fun `descricao do filtro lista o que esta ativo`() {
        val cats = listOf(Categoria(1, "Mercado", 0, TipoCategoria.SAIDA), Categoria(2, "Lazer", 0, TipoCategoria.SAIDA))
        val f = Filtro(
            periodo = Periodo.Mes(YearMonth.of(2026, 10)),
            tipo = TipoLancamento.SAIDA,
            categorias = setOf(2),
            estabelecimentos = setOf(1),
            busca = "leite",
        )
        assertEquals(
            "Outubro 2026 · Só saídas · Categorias: Lazer · Estabelecimentos: Horizonte Restaurantes S.A. · Busca: \"leite\"",
            descreverFiltro(f, cats, listOf(restaurante), hoje),
        )
        assertEquals("Dezembro 2025", descreverFiltro(Filtro(periodo = Periodo.Mes(YearMonth.of(2025, 12))), cats, emptyList(), hoje))
    }

    @Test
    fun `nome do arquivo segue o periodo`() {
        assertEquals("financas-2026-10.csv", nomeArquivo(Filtro(periodo = Periodo.Mes(YearMonth.of(2026, 10))), hoje, FormatoExportacao.CSV))
        assertEquals("financas-completo.pdf", nomeArquivo(Filtro(periodo = Periodo.Tudo), hoje, FormatoExportacao.PDF))
        assertEquals("financas-20260910-20261008.pdf", nomeArquivo(Filtro(periodo = Periodo.UltimosDias(29)), hoje, FormatoExportacao.PDF))
    }

    // ---- Layout do PDF ----

    /** Fonte "monoespaçada" fictícia: cada caractere mede metade do tamanho. */
    private val medidor = MedidorTexto { t, tamanho -> t.length * tamanho / 2 }

    @Test
    fun `relatorio tem resumo, categorias e itens quando pedido`() {
        val linhas = RelatorioLayout.montar(dados, incluirItens = true, larguraUtil = 515f, medidor = medidor)
        val resumo = linhas.filterIsInstance<LinhaRelatorio.Resumo>().single()
        assertEquals(650000L, resumo.entradas)
        assertEquals(8490L, resumo.saidas)
        assertEquals(listOf("Alimentação fora"), linhas.filterIsInstance<LinhaRelatorio.Categoria>().map { it.nome })
        assertEquals(2, linhas.filterIsInstance<LinhaRelatorio.Item>().size)
        assertEquals("0,432 KG × R$ 5,899", linhas.filterIsInstance<LinhaRelatorio.Item>()[1].detalhe)
        assertEquals(-8490L, linhas.filterIsInstance<LinhaRelatorio.Lancamento>()[0].valorComSinal)

        val semItens = RelatorioLayout.montar(dados, incluirItens = false, larguraUtil = 515f, medidor = medidor)
        assertTrue(semItens.none { it is LinhaRelatorio.Item })
    }

    @Test
    fun `paginacao repete o cabecalho da tabela e nunca estoura a pagina`() {
        val muitos = dados.copy(
            lancamentos = (1..200L).map {
                LancamentoExportado(lanc(it, TipoLancamento.SAIDA, 1000, "2026-10-02T12:00", "Compra $it"), null, emptyList())
            },
        )
        val altura = 400f
        val paginas = RelatorioLayout.paginar(RelatorioLayout.montar(muitos, false, 515f, medidor), altura)
        assertTrue(paginas.size > 5)
        paginas.forEach { p -> assertTrue("página estourou", p.sumOf { it.altura.toDouble() } <= altura) }
        // A partir da página em que a tabela começa, toda página abre com o cabeçalho.
        val inicioTabela = paginas.indexOfFirst { p -> p.any { it == LinhaRelatorio.CabecalhoTabela } }
        paginas.drop(inicioTabela + 1).forEach { p -> assertEquals(LinhaRelatorio.CabecalhoTabela, p.first()) }
        // Todos os 200 lançamentos aparecem uma única vez.
        assertEquals(200, paginas.flatten().count { it is LinhaRelatorio.Lancamento })
        assertEquals(1 + paginas.size - 1 - inicioTabela, paginas.flatten().count { it == LinhaRelatorio.CabecalhoTabela })
    }

    @Test
    fun `secao nao fica sozinha no pe da pagina`() {
        val linhas = List(7) { LinhaRelatorio.Texto("linha $it") } + listOf( // 7 × 13 = 91
            LinhaRelatorio.Secao("Lançamentos"), // 26 → caberia (117), mas não junto com a linha seguinte
            LinhaRelatorio.CabecalhoTabela,
        )
        val paginas = RelatorioLayout.paginar(linhas, 120f)
        assertEquals(2, paginas.size)
        assertEquals(LinhaRelatorio.Secao("Lançamentos"), paginas[1].first())
    }

    @Test
    fun `quebra e encurta texto pela largura`() {
        assertEquals(listOf("um dois", "tres"), RelatorioLayout.quebrar("um dois tres", 7 * 5f, 10f, medidor))
        assertEquals("abcd…", RelatorioLayout.encurtar("abcdefghij", 5 * 5f, 10f, medidor))
        assertEquals("curto", RelatorioLayout.encurtar("curto", 100f, 10f, medidor))
    }
}
