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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.dosis.android.ui.components.HairlineDivider
import app.dosis.android.ui.components.MedicineRow
import app.dosis.android.ui.components.Pill
import app.dosis.android.ui.components.SectionLabel
import app.dosis.android.ui.theme.Ink
import app.dosis.core.journal.Journal
import app.dosis.core.journal.JournalState
import app.dosis.core.model.Medicine
import app.dosis.core.model.ReminderLevel
import app.dosis.core.model.Schedule
import kotlinx.datetime.number

@Composable
fun BotiquinScreen(journal: Journal, state: JournalState, twoPane: Boolean, modifier: Modifier = Modifier) {
    var selectedId by remember { mutableStateOf<String?>(null) }
    val selected = selectedId?.let { state.medicines[it] }

    val list: @Composable (Modifier) -> Unit = { m ->
        Column(m) {
            Text("Tu botiquín.", style = MaterialTheme.typography.displayLarge)
            Text("Lo que guardas para cuidarte.", color = Ink.Muted)
            Spacer(Modifier.height(8.dp))
            state.cabinet.forEach { med ->
                Row(Modifier.fillMaxWidth().clickable { selectedId = med.id }) {
                    MedicineRow(med.appearance, med.displayName, summary(med))
                }
                HairlineDivider()
            }
            if (state.oneOffs.isNotEmpty()) {
                SectionLabel("Tomadas alguna vez")
                state.oneOffs.forEach { med ->
                    MedicineRow(med.appearance, med.displayName, null, trailing = {
                        TextButton(onClick = { journal.setInCabinet(med.id, true) }) { Text("+ Agregar") }
                    })
                }
            }
        }
    }

    if (twoPane) {
        Row(modifier.fillMaxSize().padding(32.dp), horizontalArrangement = Arrangement.spacedBy(48.dp)) {
            list(Modifier.weight(1f).verticalScroll(rememberScrollState()))
            Column(Modifier.weight(1.3f).verticalScroll(rememberScrollState())) {
                (selected ?: state.cabinet.firstOrNull())?.let { MedicineDetail(journal, it) }
            }
        }
    } else {
        Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp)) {
            if (selected != null) {
                TextButton(onClick = { selectedId = null }) { Text("← Botiquín") }
                MedicineDetail(journal, selected)
            } else {
                list(Modifier)
            }
        }
    }
}

@Composable
private fun MedicineDetail(journal: Journal, medicine: Medicine) {
    Pill(medicine.appearance, height = 56.dp)
    Spacer(Modifier.height(16.dp))
    Text(medicine.name, style = MaterialTheme.typography.displayLarge)
    medicine.strength?.let { Text(it, style = MaterialTheme.typography.headlineMedium) }
    summary(medicine)?.let { Text(it, color = Ink.Muted, modifier = Modifier.padding(top = 4.dp)) }
    medicine.stock?.let {
        Spacer(Modifier.height(16.dp))
        Text("Quedan ${it.remaining}", style = MaterialTheme.typography.headlineMedium)
        Text("Avisarme al quedar ${it.alertAt}", style = MaterialTheme.typography.bodySmall, color = Ink.Muted)
    }
    var editing by remember(medicine.id) { mutableStateOf(false) }
    SectionLabel("Recordatorio")
    Text(summary(medicine) ?: "Sin recordatorio", style = MaterialTheme.typography.titleMedium)
    medicine.treatment?.endDate?.let {
        Text("Hasta el ${it.day}/${it.month.number}", style = MaterialTheme.typography.bodySmall, color = Ink.Muted)
    }
    OutlinedButton(onClick = { editing = true }, modifier = Modifier.padding(top = 8.dp)) {
        Text(if (medicine.treatment == null) "+ Agregar recordatorio" else "Cambiar recordatorio")
    }
    if (editing) {
        ReminderEditorSheet(
            medicine = medicine,
            onSave = { t ->
                journal.upsertMedicine(medicine.copy(treatment = t, inCabinet = true))
                editing = false
            },
            onDismiss = { editing = false },
        )
    }

    val data by journal.interactionData.collectAsState()
    val cabinet by journal.state.collectAsState()
    InteractionSections(remember(medicine, data, cabinet) { journal.preview(medicine) })
}

private fun summary(m: Medicine): String? {
    val t = m.treatment ?: return null
    val schedule = when (val s = t.schedule) {
        is Schedule.Daily -> "Diario · " + s.times.joinToString(" y ") { "%02d:%02d".format(it.hour, it.minute) }
        is Schedule.SpecificDays -> s.days.sortedBy { it.ordinal }.joinToString(" ") { "LMXJVSD"[it.ordinal].toString() } +
            " · " + s.times.joinToString(" y ") { "%02d:%02d".format(it.hour, it.minute) }
        is Schedule.EveryHours -> "Cada ${s.hours} h desde las %02d:%02d".format(s.firstDose.hour, s.firstDose.minute)
        is Schedule.Cyclic -> "Cíclico ${s.daysOn}/${s.daysOff}"
        Schedule.AsNeeded -> "Cuando lo necesite"
    }
    if (t.schedule == Schedule.AsNeeded) return schedule
    val level = if (t.reminderLevel == ReminderLevel.STRONG) "Recordatorio fuerte" else "Recordatorio suave"
    return "$schedule · $level"
}
