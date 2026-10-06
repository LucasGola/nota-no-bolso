package dev.lucasgola.financas.util

import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** Datas são gravadas em UTC ([Instant]) e exibidas sempre neste fuso. */
val ZONA: ZoneId = ZoneId.of("America/Sao_Paulo")

private val fmtData = DateTimeFormatter.ofPattern("dd/MM/yyyy", LOCALE_BR)
private val fmtDiaExtrato = DateTimeFormatter.ofPattern("EEE, dd 'de' MMMM yyyy", LOCALE_BR)

fun Instant.dataLocal(): LocalDate = atZone(ZONA).toLocalDate()

fun LocalDate.formatar(): String = format(fmtData)

fun LocalDate.formatarDiaExtrato(): String = format(fmtDiaExtrato).replaceFirstChar { it.uppercase() }

/** Lançamento manual: só a data importa; guarda ao meio-dia local para não "mudar de dia" com fuso. */
fun LocalDate.meioDia(): Instant = atTime(LocalTime.NOON).atZone(ZONA).toInstant()
