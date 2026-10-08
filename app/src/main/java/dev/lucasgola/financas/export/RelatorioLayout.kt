package dev.lucasgola.financas.export

import dev.lucasgola.financas.data.TipoLancamento
import dev.lucasgola.financas.graficos.porCategoria
import dev.lucasgola.financas.util.ZONA
import dev.lucasgola.financas.util.formatarDecimalBr
import dev.lucasgola.financas.util.formatarPrecoUnitario
import java.time.format.DateTimeFormatter

// Layout do relatório em PDF, independente do Android: produz linhas com altura conhecida e
// as distribui em páginas. O desenho (RelatorioPdf) só percorre o resultado.

/** Mede a largura de um texto num tamanho de fonte (em pontos). Injetado para testar sem Android. */
fun interface MedidorTexto {
    fun largura(texto: String, tamanho: Float): Float
}

sealed interface LinhaRelatorio {
    val altura: Float

    data class Titulo(val texto: String) : LinhaRelatorio { override val altura = 24f }
    data class Texto(val texto: String) : LinhaRelatorio { override val altura = 13f }
    data class Secao(val titulo: String) : LinhaRelatorio { override val altura = 26f }
    data class Resumo(val entradas: Long, val saidas: Long) : LinhaRelatorio { override val altura = 34f }
    data class Categoria(val nome: String, val valor: Long, val fracao: Double) : LinhaRelatorio { override val altura = 15f }
    data object CabecalhoTabela : LinhaRelatorio { override val altura = 18f }
    data class Lancamento(val data: String, val descricao: String, val categoria: String, val valorComSinal: Long) :
        LinhaRelatorio { override val altura = 15f }
    data class Item(val descricao: String, val detalhe: String, val valor: Long) : LinhaRelatorio { override val altura = 12f }
    data class Espaco(override val altura: Float) : LinhaRelatorio
}

object RelatorioLayout {
    const val TAMANHO_TEXTO = 9f

    private val fmtData = DateTimeFormatter.ofPattern("dd/MM/yyyy")

    fun montar(
        dados: DadosExportacao,
        incluirItens: Boolean,
        larguraUtil: Float,
        medidor: MedidorTexto,
    ): List<LinhaRelatorio> = buildList {
        add(LinhaRelatorio.Titulo("Relatório de lançamentos"))
        quebrar(dados.descricaoFiltro, larguraUtil, TAMANHO_TEXTO, medidor).forEach { add(LinhaRelatorio.Texto(it)) }
        add(LinhaRelatorio.Espaco(6f))

        val lista = dados.lancamentos.map { it.dados }
        add(LinhaRelatorio.Secao("Resumo"))
        add(
            LinhaRelatorio.Resumo(
                entradas = lista.filter { it.lancamento.tipo == TipoLancamento.ENTRADA }.sumOf { it.lancamento.valorCentavos },
                saidas = lista.filter { it.lancamento.tipo == TipoLancamento.SAIDA }.sumOf { it.lancamento.valorCentavos },
            )
        )

        val categorias = porCategoria(lista, TipoLancamento.SAIDA)
        if (categorias.isNotEmpty()) {
            add(LinhaRelatorio.Secao("Saídas por categoria"))
            categorias.forEach { add(LinhaRelatorio.Categoria(it.nome, it.totalCentavos, it.fracao)) }
        }

        add(LinhaRelatorio.Secao("Lançamentos (${dados.lancamentos.size})"))
        if (dados.lancamentos.isEmpty()) {
            add(LinhaRelatorio.Texto("Nenhum lançamento neste filtro."))
            return@buildList
        }
        add(LinhaRelatorio.CabecalhoTabela)
        for (e in dados.lancamentos) {
            val l = e.dados.lancamento
            add(
                LinhaRelatorio.Lancamento(
                    data = l.dataHora.atZone(ZONA).format(fmtData),
                    descricao = l.descricao.ifBlank { e.dados.categoriaNome },
                    categoria = e.dados.categoriaNome,
                    valorComSinal = if (l.tipo == TipoLancamento.SAIDA) -l.valorCentavos else l.valorCentavos,
                )
            )
            if (incluirItens) e.itens.forEach { i ->
                add(
                    LinhaRelatorio.Item(
                        descricao = i.descricao,
                        detalhe = "${formatarDecimalBr(i.quantidade)} ${i.unidade} × R$ ${formatarPrecoUnitario(i.valorUnitario)}",
                        valor = i.valorTotalCentavos,
                    )
                )
            }
        }
    }

    /**
     * Distribui as linhas em páginas de [alturaUtil]. Na tabela de lançamentos, cada página nova
     * repete o cabeçalho; um título de seção nunca fica sozinho no pé da página.
     */
    fun paginar(linhas: List<LinhaRelatorio>, alturaUtil: Float): List<List<LinhaRelatorio>> {
        val paginas = mutableListOf<MutableList<LinhaRelatorio>>(mutableListOf())
        var usado = 0f
        var naTabela = false

        fun novaPagina() {
            paginas += mutableListOf<LinhaRelatorio>()
            usado = 0f
            if (naTabela) {
                paginas.last() += LinhaRelatorio.CabecalhoTabela
                usado = LinhaRelatorio.CabecalhoTabela.altura
            }
        }

        linhas.forEachIndexed { i, linha ->
            when (linha) {
                is LinhaRelatorio.Secao -> naTabela = false
                LinhaRelatorio.CabecalhoTabela -> naTabela = true
                else -> Unit
            }
            // Seção (e cabeçalho) precisam caber junto com a linha seguinte.
            val proxima = linhas.getOrNull(i + 1)
            val exigido = linha.altura + if (linha is LinhaRelatorio.Secao || linha == LinhaRelatorio.CabecalhoTabela) proxima?.altura ?: 0f else 0f
            if (usado > 0f && usado + exigido > alturaUtil) {
                // O cabeçalho que dispara a quebra não deve ser duplicado na página nova.
                val repetiria = linha == LinhaRelatorio.CabecalhoTabela
                if (repetiria) naTabela = false
                novaPagina()
                if (repetiria) naTabela = true
            }
            if (linha is LinhaRelatorio.Espaco && usado == 0f) return@forEachIndexed // sem espaço no topo
            paginas.last() += linha
            usado += linha.altura
        }
        return paginas
    }

    /** Quebra o texto em linhas que caibam na largura, por palavras. */
    fun quebrar(texto: String, largura: Float, tamanho: Float, medidor: MedidorTexto): List<String> {
        if (texto.isBlank()) return emptyList()
        val linhas = mutableListOf<String>()
        var atual = ""
        for (palavra in texto.split(' ')) {
            val tentativa = if (atual.isEmpty()) palavra else "$atual $palavra"
            if (medidor.largura(tentativa, tamanho) <= largura || atual.isEmpty()) {
                atual = tentativa
            } else {
                linhas += atual
                atual = palavra
            }
        }
        if (atual.isNotEmpty()) linhas += atual
        return linhas
    }

    /** Corta o texto com "…" para caber na largura. */
    fun encurtar(texto: String, largura: Float, tamanho: Float, medidor: MedidorTexto): String {
        if (medidor.largura(texto, tamanho) <= largura) return texto
        var fim = texto.length
        while (fim > 0 && medidor.largura(texto.take(fim) + "…", tamanho) > largura) fim--
        return texto.take(fim).trimEnd() + "…"
    }
}
