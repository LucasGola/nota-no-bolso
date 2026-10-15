package dev.lucasgola.financas.filtro

import androidx.sqlite.db.SimpleSQLiteQuery
import java.text.Normalizer
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
            // Também entra a nota com algum item classificado numa das categorias (ex.: carne na compra do mercado).
            val marcadores = filtro.categorias.joinToString { "?" }
            condicoes += """
                (l.categoriaId IN ($marcadores) OR EXISTS (
                    SELECT 1 FROM nota_fiscal n JOIN item_nota i ON i.notaId = n.id
                    WHERE n.lancamentoId = l.id AND i.categoriaId IN ($marcadores)
                ))
            """.trimIndent()
            args.addAll(filtro.categorias)
            args.addAll(filtro.categorias)
        }
        if (filtro.estabelecimentos.isNotEmpty()) {
            condicoes += "l.estabelecimentoId IN (${filtro.estabelecimentos.joinToString { "?" }})"
            args.addAll(filtro.estabelecimentos)
        }
        if (filtro.metodosPagamento.isNotEmpty()) {
            condicoes += "l.metodoPagamentoId IN (${filtro.metodosPagamento.joinToString { "?" }})"
            args.addAll(filtro.metodosPagamento)
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
            // Sem acento dos dois lados: "pao" encontra "PÃO".
            condicoes += """
                (${semAcentoSql("l.descricao")} LIKE ? ESCAPE '\' OR EXISTS (
                    SELECT 1 FROM nota_fiscal n JOIN item_nota i ON i.notaId = n.id
                    WHERE n.lancamentoId = l.id AND ${semAcentoSql("i.descricao")} LIKE ? ESCAPE '\'
                ))
            """.trimIndent()
            val padrao = "%${escaparLike(semAcento(termo))}%"
            args += padrao
            args += padrao
        }

        val where = if (condicoes.isEmpty()) "" else "WHERE " + condicoes.joinToString(" AND ")
        return SqlFiltrado("$BASE $where ORDER BY l.dataHora DESC, l.id DESC", args)
    }

    // O LIKE do SQLite só ignora maiúsculas em ASCII e não conhece acentos ("ã" ≠ "Ã" ≠ "a").
    // A coluna é normalizada com REPLACE na própria consulta, sem coluna extra nem migração do banco;
    // no volume de um app pessoal, o custo de varrer as linhas é desprezível.
    private val acentos = mapOf(
        'a' to "áàâãäÁÀÂÃÄ", 'e' to "éèêëÉÈÊË", 'i' to "íìîïÍÌÎÏ",
        'o' to "óòôõöÓÒÔÕÖ", 'u' to "úùûüÚÙÛÜ", 'c' to "çÇ", 'n' to "ñÑ",
    )

    internal fun semAcentoSql(coluna: String): String =
        acentos.entries.fold(coluna) { expr, (base, variantes) ->
            variantes.fold(expr) { e, ch -> "REPLACE($e, '$ch', '$base')" }
        }

    /** Remove os acentos do termo digitado, como [semAcentoSql] faz com a coluna. */
    fun semAcento(texto: String): String =
        Normalizer.normalize(texto, Normalizer.Form.NFD).replace(marcasDiacriticas, "")

    private val marcasDiacriticas = Regex("\\p{Mn}+")

    /** "%" e "_" digitados pelo usuário são literais, não curingas. */
    fun escaparLike(texto: String): String =
        texto.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")
}
