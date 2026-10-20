package dev.lucasgola.financas.ui.categorias

import dev.lucasgola.financas.data.Categoria
import dev.lucasgola.financas.data.TipoCategoria
import dev.lucasgola.financas.data.TipoLancamento
import dev.lucasgola.financas.filtro.ConsultaLancamentos.semAcento

/**
 * Filtro da lista de categorias. "Entrada" e "Saída" incluem as de "entrada e saída" (ex.: Outros),
 * que servem para os dois: são as categorias que aparecem ao lançar uma entrada ou uma saída.
 */
enum class FiltroTipoCategoria(val rotulo: String) {
    TODAS("Todas"),
    ENTRADA("Entrada"),
    SAIDA("Saída"),
    ITEM("Só itens");

    fun inclui(tipo: TipoCategoria): Boolean = when (this) {
        TODAS -> true
        ENTRADA -> tipo.aceita(TipoLancamento.ENTRADA)
        SAIDA -> tipo.aceita(TipoLancamento.SAIDA)
        ITEM -> tipo == TipoCategoria.ITEM
    }

    /** Tipo sugerido para uma categoria nova criada com este filtro ativo. */
    val tipoParaNova: TipoCategoria
        get() = when (this) {
            ENTRADA -> TipoCategoria.ENTRADA
            ITEM -> TipoCategoria.ITEM
            TODAS, SAIDA -> TipoCategoria.SAIDA
        }
}

/** Busca pelo nome sem diferenciar maiúsculas nem acentos ("acai" encontra "Açaí"). */
fun filtrarCategorias(lista: List<Categoria>, tipo: FiltroTipoCategoria, busca: String): List<Categoria> {
    val termo = semAcento(busca.trim()).lowercase()
    return lista.filter { tipo.inclui(it.tipo) && (termo.isEmpty() || semAcento(it.nome).lowercase().contains(termo)) }
}
