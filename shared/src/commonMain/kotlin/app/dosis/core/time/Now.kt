package app.dosis.core.time

import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

@OptIn(ExperimentalTime::class)
fun nowLocal(): LocalDateTime = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())

private val weekdays = listOf("Lunes", "Martes", "Miércoles", "Jueves", "Viernes", "Sábado", "Domingo")
private val months = listOf(
    "enero", "febrero", "marzo", "abril", "mayo", "junio",
    "julio", "agosto", "septiembre", "octubre", "noviembre", "diciembre",
)

/** "Domingo 8 de noviembre de 2026" */
fun LocalDateTime.longSpanishDate(): String =
    "${weekdays[date.dayOfWeek.ordinal]} ${date.day} de ${months[date.month.ordinal]} de ${date.year}"

fun monthName(monthIndex0: Int): String = months[monthIndex0].replaceFirstChar { it.uppercase() }

/** "08:10" */
fun LocalDateTime.hhmm(): String = "${hour.toString().padStart(2, '0')}:${minute.toString().padStart(2, '0')}"
