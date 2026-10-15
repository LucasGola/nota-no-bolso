package dev.lucasgola.financas.filtro

import dev.lucasgola.financas.data.OrigemLancamento
import dev.lucasgola.financas.data.TipoLancamento
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.time.DateTimeException
import java.time.LocalDate
import java.time.YearMonth

/**
 * Guarda o filtro entre aberturas do app. O período "mês" volta sempre para o mês atual:
 * quem abre o app num mês novo quer ver o mês novo, não o que estava olhando semanas atrás.
 * Os demais períodos e todas as restrições (tipo, categorias, busca...) são mantidos.
 */
object FiltroSalvo {

    fun codificar(f: Filtro): String = JSONObject().apply {
        put("periodo", periodo(f.periodo))
        f.tipo?.let { put("tipo", it.name) }
        put("categorias", JSONArray(f.categorias.toList()))
        put("estabelecimentos", JSONArray(f.estabelecimentos.toList()))
        put("metodosPagamento", JSONArray(f.metodosPagamento.toList()))
        f.origem?.let { put("origem", it.name) }
        f.valorMinCentavos?.let { put("valorMin", it) }
        f.valorMaxCentavos?.let { put("valorMax", it) }
        put("busca", f.busca)
    }.toString()

    /** Filtro salvo, ou o padrão se não houver nada salvo ou o texto for inválido. */
    fun decodificar(texto: String?, hoje: LocalDate): Filtro {
        if (texto.isNullOrBlank()) return Filtro(periodo = Periodo.mesAtual(hoje))
        return try {
            val o = JSONObject(texto)
            Filtro(
                periodo = periodo(o.getJSONObject("periodo"), hoje),
                tipo = o.optString("tipo").takeIf { it.isNotEmpty() }?.let(TipoLancamento::valueOf),
                categorias = o.getJSONArray("categorias").longs(),
                estabelecimentos = o.getJSONArray("estabelecimentos").longs(),
                metodosPagamento = o.optJSONArray("metodosPagamento")?.longs().orEmpty(),
                origem = o.optString("origem").takeIf { it.isNotEmpty() }?.let(OrigemLancamento::valueOf),
                valorMinCentavos = if (o.has("valorMin")) o.getLong("valorMin") else null,
                valorMaxCentavos = if (o.has("valorMax")) o.getLong("valorMax") else null,
                busca = o.optString("busca"),
            )
        } catch (e: JSONException) {
            Filtro(periodo = Periodo.mesAtual(hoje))
        } catch (e: IllegalArgumentException) { // enum inválido
            Filtro(periodo = Periodo.mesAtual(hoje))
        } catch (e: DateTimeException) {
            Filtro(periodo = Periodo.mesAtual(hoje))
        }
    }

    private fun periodo(p: Periodo): JSONObject = when (p) {
        is Periodo.Mes -> JSONObject().put("tipo", "mes")
        is Periodo.UltimosDias -> JSONObject().put("tipo", "dias").put("dias", p.dias)
        is Periodo.Ano -> JSONObject().put("tipo", "ano").put("ano", p.ano)
        is Periodo.Personalizado -> JSONObject().put("tipo", "personalizado").put("de", p.de.toString()).put("ate", p.ate.toString())
        Periodo.Tudo -> JSONObject().put("tipo", "tudo")
    }

    private fun periodo(o: JSONObject, hoje: LocalDate): Periodo = when (o.getString("tipo")) {
        "dias" -> Periodo.UltimosDias(o.getInt("dias"))
        "ano" -> Periodo.Ano(o.getInt("ano"))
        "personalizado" -> Periodo.Personalizado(LocalDate.parse(o.getString("de")), LocalDate.parse(o.getString("ate")))
        "tudo" -> Periodo.Tudo
        else -> Periodo.Mes(YearMonth.from(hoje))
    }

    private fun JSONArray.longs(): Set<Long> = (0 until length()).map { getLong(it) }.toSet()
}
