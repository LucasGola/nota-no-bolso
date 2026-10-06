package dev.lucasgola.financas.filtro

import androidx.sqlite.db.SimpleSQLiteQuery
import java.time.LocalDate

/** SQL + argumentos gerados a partir de um [Filtro]. Separado do Room para ser testável na JVM. */
data class SqlFiltrado(val sql: String, val args: List<Any>) {
    fun paraRoom() = SimpleSQLiteQuery(sql, args.toTypedArray())
}

object ConsultaLancamentos {

    private const val BASE = """
        SELECT l.*, c.nome AS categoriaNome, c.cor AS categoriaCor
        FROM lancamento l JOIN categoria c ON c.id = l.categoriaId
    """

    fun montar(filtro: Filtro, hoje: LocalDate): SqlFiltrado {
        val condicoes = mutableListOf<String>()
        val args = mutableListOf<Any>()

        filtro.periodo.intervalo(hoje)?.emInstants()?.let { (inicio, fim) ->
            condicoes += "l.dataHora >= ? AND l.dataHora < ?"
            args += inicio.toEpochMilli()
            args += fim.toEpochMilli()
        }
        filtro.tipo?.let {
            condicoes += "l.tipo = ?"
            args += it.name
        }
        if (filtro.categorias.isNotEmpty()) {
            condicoes += "l.categoriaId IN (${filtro.categorias.joinToString { "?" }})"
            args.addAll(filtro.categorias)
        }
        if (filtro.estabelecimentos.isNotEmpty()) {
            condicoes += "l.estabelecimentoId IN (${filtro.estabelecimentos.joinToString { "?" }})"
            args.addAll(filtro.estabelecimentos)
        }
        filtro.origem?.let {
            condicoes += "l.origem = ?"
            args += it.name
        }
        filtro.valorMinCentavos?.let {
            condicoes += "l.valorCentavos >= ?"
            args += it
        }
        filtro.valorMaxCentavos?.let {
            condicoes += "l.valorCentavos <= ?"
            args += it
        }
        filtro.busca.trim().takeIf { it.isNotEmpty() }?.let { termo ->
            // Procura na descrição do lançamento e na descrição dos itens da nota vinculada.
            condicoes += """
                (l.descricao LIKE ? ESCAPE '\' OR EXISTS (
                    SELECT 1 FROM nota_fiscal n JOIN item_nota i ON i.notaId = n.id
                    WHERE n.lancamentoId = l.id AND i.descricao LIKE ? ESCAPE '\'
                ))
            """.trimIndent()
            val padrao = "%${escaparLike(termo)}%"
            args += padrao
            args += padrao
        }

        val where = if (condicoes.isEmpty()) "" else "WHERE " + condicoes.joinToString(" AND ")
        return SqlFiltrado("$BASE $where ORDER BY l.dataHora DESC, l.id DESC", args)
    }

    /** "%" e "_" digitados pelo usuário são literais, não curingas. */
    fun escaparLike(texto: String): String =
        texto.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")
}
