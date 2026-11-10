package app.dosis.android.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.dosis.android.ui.components.HairlineDivider
import app.dosis.android.ui.components.MedicineRow
import app.dosis.android.ui.components.Pill
import app.dosis.android.ui.components.SectionLabel
import app.dosis.android.ui.theme.Ink
import app.dosis.core.journal.Journal
import app.dosis.core.journal.JournalState
import app.dosis.core.model.Dose
import app.dosis.core.model.DoseStatus
import app.dosis.core.model.Medicine
import app.dosis.core.time.hhmm
import app.dosis.core.time.longSpanishDate
import app.dosis.core.time.nowLocal

@Composable
fun HoyScreen(journal: Journal, state: JournalState, twoPane: Boolean, modifier: Modifier = Modifier) {
    var pending by remember { mutableStateOf<Medicine?>(null) }

    if (twoPane) {
        Row(modifier.fillMaxSize().padding(32.dp), horizontalArrangement = Arrangement.spacedBy(48.dp)) {
            QuickLog(journal, state, onPick = { pending = it }, modifier = Modifier.weight(1f))
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) { TodayTimeline(state) }
        }
    } else {
        Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 16.dp)) {
            QuickLog(journal, state, onPick = { pending = it })
            TodayTimeline(state)
        }
    }

    pending?.let { med ->
        LogDoseSheet(
            journal = journal,
            medicine = med,
            onDismiss = { pending = null },
        )
    }
}

@Composable
private fun QuickLog(journal: Journal, state: JournalState, onPick: (Medicine) -> Unit, modifier: Modifier = Modifier) {
    var query by remember { mutableStateOf("") }
    val now = nowLocal()

    Column(modifier) {
        Text("dosis", style = MaterialTheme.typography.headlineMedium, color = Ink.Blue)
        Spacer(Modifier.height(16.dp))
        Text("Hola, ¿cómo vas?", style = MaterialTheme.typography.headlineLarge)
        Text(now.longSpanishDate(), style = MaterialTheme.typography.bodySmall, color = Ink.Muted)
        ReminderSetupCard(state)
        Spacer(Modifier.height(24.dp))
        Text("¿Qué tomaste?", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            placeholder = { Text("Busca una medicina") },
            singleLine = true,
            shape = RoundedCornerShape(50),
            colors = OutlinedTextFieldDefaults.colors(unfocusedBorderColor = Ink.Blue, focusedBorderColor = Ink.Blue),
            modifier = Modifier.fillMaxWidth(),
        )

        if (query.isNotBlank()) {
            val results = journal.suggest(query)
            results.forEach { m ->
                Row(Modifier.fillMaxWidth().clickable { onPick(m); query = "" }) {
                    MedicineRow(
                        m.appearance, m.displayName,
                        when {
                            m.inCabinet -> "En tu botiquín"
                            journal.isBrandName(m) -> journal.ingredientNames(m).joinToString(" + ")
                            m.id.startsWith(Journal.LEXICON_ID) -> "Medicina conocida"
                            else -> null
                        },
                    )
                }
            }
            Text(
                "+ Registrar \"${query.trim()}\" como nueva medicina",
                color = Ink.Blue,
                modifier = Modifier.padding(vertical = 12.dp).clickable { onPick(journal.resolve(query)); query = "" },
            )
        } else {
            SectionLabel("Tus recientes · toca para registrar")
            Row(horizontalArrangement = Arrangement.spacedBy(28.dp)) {
                state.recent(3).forEach { m ->
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.clickable { onPick(m) },
                    ) {
                        Pill(m.appearance, height = 32.dp)
                        Spacer(Modifier.height(6.dp))
                        Text(m.name, style = MaterialTheme.typography.bodySmall)
                        m.strength?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = Ink.Muted) }
                    }
                }
            }
        }

        journal.nextDose(now)?.let { next ->
            SectionLabel("Próxima toma · programada")
            Row(verticalAlignment = Alignment.CenterVertically) {
                Pill(next.medicine.appearance, height = 18.dp)
                Spacer(Modifier.width(10.dp))
                Text("${next.medicine.displayName} · ${next.at.hhmm()}", style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}

@Composable
private fun TodayTimeline(state: JournalState) {
    val today = nowLocal().date
    val doses = state.dosesOn(today)
    SectionLabel("Hoy")
    if (doses.isEmpty()) {
        Text("Todavía no registras nada hoy.", color = Ink.Muted)
    }
    doses.forEach { d ->
        DoseRow(state, d)
        HairlineDivider()
    }
    state.cabinet.filter { it.stock?.isLow == true }.forEach { m ->
        Text(
            "Te quedan ${m.stock!!.remaining} de ${m.name}",
            color = Ink.Blue,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(top = 12.dp),
        )
    }
}

@Composable
fun DoseRow(state: JournalState, d: Dose) {
    val m = state.medicines[d.medicineId] ?: return
    MedicineRow(
        appearance = m.appearance,
        title = m.displayName,
        subtitle = d.note?.text ?: d.note?.chips?.firstOrNull(),
        leading = d.at.hhmm(),
        trailing = {
            Text(
                if (d.status == DoseStatus.TAKEN) "✓ Tomada" else "— Omitida",
                color = if (d.status == DoseStatus.TAKEN) Ink.Blue else Ink.Muted,
                style = MaterialTheme.typography.bodySmall,
            )
        },
    )
}
