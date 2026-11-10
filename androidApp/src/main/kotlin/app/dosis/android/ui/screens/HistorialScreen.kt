package app.dosis.android.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.dosis.android.ui.components.HairlineDivider
import app.dosis.android.ui.components.SectionLabel
import app.dosis.android.ui.theme.Ink
import app.dosis.core.journal.JournalState
import app.dosis.core.model.DoseStatus
import app.dosis.core.time.monthName
import app.dosis.core.time.nowLocal
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus
import kotlinx.datetime.plus

/** History has NO interaction warnings — by design. */
@Composable
fun HistorialScreen(state: JournalState, twoPane: Boolean, modifier: Modifier = Modifier) {
    val today = remember { nowLocal().date }
    var selected by remember { mutableStateOf(today) }

    val calendar: @Composable (Modifier) -> Unit = { m ->
        Column(m) {
            Text("Tu historial.", style = MaterialTheme.typography.displayLarge)
            MonthGrid(state, selected.year, selected.month.ordinal, selected, today) { selected = it }
        }
    }
    val day: @Composable (Modifier) -> Unit = { m ->
        Column(m) {
            SectionLabel("${selected.day} de ${monthName(selected.month.ordinal).lowercase()}")
            val doses = state.dosesOn(selected)
            if (doses.isEmpty()) Text("Nada registrado este día.", color = Ink.Muted)
            doses.forEach { DoseRow(state, it); HairlineDivider() }
        }
    }

    if (twoPane) {
        Row(modifier.fillMaxSize().padding(32.dp), horizontalArrangement = Arrangement.spacedBy(48.dp)) {
            calendar(Modifier.weight(1f))
            day(Modifier.weight(1f).verticalScroll(rememberScrollState()))
        }
    } else {
        Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp)) {
            calendar(Modifier)
            day(Modifier)
        }
    }
}

@Composable
private fun MonthGrid(
    state: JournalState,
    year: Int,
    month0: Int,
    selected: LocalDate,
    today: LocalDate,
    onSelect: (LocalDate) -> Unit,
) {
    val first = LocalDate(year, month0 + 1, 1)
    val daysInMonth = first.plus(DatePeriod(months = 1)).minus(DatePeriod(days = 1)).day
    val lead = first.dayOfWeek.ordinal // Monday = 0
    Text("${monthName(month0)} $year", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(vertical = 16.dp))
    Row(Modifier.fillMaxWidth()) {
        listOf("L", "M", "X", "J", "V", "S", "D").forEach {
            Text(it, Modifier.weight(1f), textAlign = TextAlign.Center, color = Ink.Blue, style = MaterialTheme.typography.labelSmall)
        }
    }
    val cells = List(lead) { null } + (1..daysInMonth).map { LocalDate(year, month0 + 1, it) }
    cells.chunked(7).forEach { week ->
        Row(Modifier.fillMaxWidth()) {
            (0 until 7).forEach { i ->
                val date = week.getOrNull(i)
                Box(Modifier.weight(1f).aspectRatio(1f), contentAlignment = Alignment.Center) {
                    if (date != null) {
                        val isSel = date == selected
                        val doses = state.dosesOn(date)
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .size(40.dp)
                                .background(if (isSel) Ink.Pink else Color.Transparent, CircleShape)
                                .clickable { onSelect(date) },
                            verticalArrangement = Arrangement.Center,
                        ) {
                            Text(
                                "${date.day}",
                                color = when {
                                    isSel -> Color.White
                                    date > today -> Ink.Muted
                                    else -> Ink.Text
                                },
                            )
                            if (doses.isNotEmpty()) {
                                Text(
                                    if (doses.all { it.status == DoseStatus.TAKEN }) "✓" else "—",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (isSel) Color.White else Ink.Blue,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
