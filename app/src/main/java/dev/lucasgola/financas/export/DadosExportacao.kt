package dev.lucasgola.financas.export

import dev.lucasgola.financas.data.AppDatabase
import dev.lucasgola.financas.data.Estabelecimento
import dev.lucasgola.financas.data.ItemNota
import dev.lucasgola.financas.data.LancamentoComCategoria
import dev.lucasgola.financas.filtro.ConsultaLancamentos
import dev.lucasgola.financas.filtro.Filtro
import dev.lucasgola.financas.filtro.descreverFiltro
import java.time.LocalDate

/** Um lançamento pronto para exportar, com o que vem de outras tabelas já resolvido. */
data class LancamentoExportado(
    val dados: LancamentoComCategoria,
    val estabelecimento: Estabelecimento?,
    val itens: List<ItemNota>,
)

data class DadosExportacao(
    /** Período e filtros em texto, para o cabeçalho do relatório. */
    val descricaoFiltro: String,
    val lancamentos: List<LancamentoExportado>,
)

/** Lê do banco tudo o que o filtro seleciona, incluindo estabelecimentos e itens das notas. */
suspend fun carregarDadosExportacao(db: AppDatabase, filtro: Filtro, hoje: LocalDate): DadosExportacao {
    val lista = db.lancamentoDao().listarFiltrado(ConsultaLancamentos.montar(filtro, hoje).paraRoom())

    val estabelecimentos = db.estabelecimentoDao().listarTodos().associateBy { it.id }
    val categorias = db.categoriaDao().listarTodas()

    // Em lotes: o SQLite limita a quantidade de parâmetros por consulta.
    val notaDao = db.notaDao()
    val notas = lista.map { it.lancamento.id }.chunked(500).flatMap { notaDao.notasDosLancamentos(it) }
    val itensPorNota = notas.map { it.id }.chunked(500).flatMap { notaDao.itensDasNotas(it) }.groupBy { it.notaId }
    val itensPorLancamento = notas.associate { it.lancamentoId to itensPorNota[it.id].orEmpty() }

    return DadosExportacao(
        descricaoFiltro = descreverFiltro(filtro, categorias, estabelecimentos.values.toList(), hoje),
        lancamentos = lista.map {
            LancamentoExportado(
                dados = it,
                estabelecimento = it.lancamento.estabelecimentoId?.let(estabelecimentos::get),
                itens = itensPorLancamento[it.lancamento.id].orEmpty(),
            )
        },
    )
}
