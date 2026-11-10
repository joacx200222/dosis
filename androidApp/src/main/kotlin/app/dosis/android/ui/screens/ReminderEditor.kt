package app.dosis.android.ui.screens

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import app.dosis.android.reminders.ReminderPermissions
import app.dosis.android.ui.components.SectionLabel
import app.dosis.android.ui.theme.Ink
import app.dosis.core.journal.JournalState
import app.dosis.core.model.Medicine
import app.dosis.core.model.ReminderLevel
import app.dosis.core.model.Schedule
import app.dosis.core.model.Treatment
import app.dosis.core.time.nowLocal
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalTime
import kotlinx.datetime.daysUntil
import kotlinx.datetime.plus

private enum class Mode(val label: String) {
    DAILY("Todos los días"),
    DAYS("Días específicos"),
    EVERY("Cada X horas"),
    CYCLIC("Cíclico"),
    AS_NEEDED("Cuando lo necesite"),
}

private val dayLabels = listOf(
    DayOfWeek.MONDAY to "L", DayOfWeek.TUESDAY to "M", DayOfWeek.WEDNESDAY to "X", DayOfWeek.THURSDAY to "J",
    DayOfWeek.FRIDAY to "V", DayOfWeek.SATURDAY to "S", DayOfWeek.SUNDAY to "D",
)

private fun LocalTime.label() = "%02d:%02d".format(hour, minute)

/** Treatment and reminder editor. Saving with "Cuando lo necesite" keeps the medicine without alarms. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ReminderEditorSheet(medicine: Medicine, onSave: (Treatment?) -> Unit, onDismiss: () -> Unit) {
    val today = nowLocal().date
    val t = medicine.treatment
    val s = t?.schedule

    var mode by remember {
        mutableStateOf(
            when (s) {
                is Schedule.Daily, null -> Mode.DAILY
                is Schedule.SpecificDays -> Mode.DAYS
                is Schedule.EveryHours -> Mode.EVERY
                is Schedule.Cyclic -> Mode.CYCLIC
                Schedule.AsNeeded -> Mode.AS_NEEDED
            },
        )
    }
    var times by remember {
        mutableStateOf(
            when (s) {
                is Schedule.Daily -> s.times
                is Schedule.SpecificDays -> s.times
                is Schedule.Cyclic -> s.times
                else -> listOf(LocalTime(8, 0))
            },
        )
    }
    var days by remember { mutableStateOf((s as? Schedule.SpecificDays)?.days ?: setOf(DayOfWeek.MONDAY)) }
    var hours by remember { mutableIntStateOf((s as? Schedule.EveryHours)?.hours ?: 8) }
    var firstDose by remember { mutableStateOf((s as? Schedule.EveryHours)?.firstDose ?: LocalTime(8, 0)) }
    var daysOn by remember { mutableIntStateOf((s as? Schedule.Cyclic)?.daysOn ?: 21) }
    var daysOff by remember { mutableIntStateOf((s as? Schedule.Cyclic)?.daysOff ?: 7) }
    var strong by remember { mutableStateOf(t?.reminderLevel == ReminderLevel.STRONG) }
    var followUp by remember { mutableStateOf(t?.followUpMinutes != null || t == null) }
    var hasEnd by remember { mutableStateOf(t?.endDate != null) }
    var durationDays by remember {
        mutableIntStateOf(t?.endDate?.let { (t.startDate.daysUntil(it) + 1).coerceAtLeast(1) } ?: 7)
    }
    // Which time the picker edits: index into times, -1 = new time, -2 = first dose (every X hours).
    var picking by remember { mutableStateOf<Int?>(null) }

    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheet, containerColor = Ink.Paper) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 24.dp).padding(bottom = 32.dp)) {
            Text("Recordatorio", style = MaterialTheme.typography.headlineMedium)
            Text(medicine.displayName, color = Ink.Muted)

            SectionLabel("¿Cada cuánto?")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Mode.entries.forEach { m ->
                    FilterChip(
                        selected = mode == m,
                        onClick = { mode = m },
                        label = { Text(m.label) },
                        colors = FilterChipDefaults.filterChipColors(selectedContainerColor = Ink.Pink),
                    )
                }
            }

            when (mode) {
                Mode.DAYS -> {
                    SectionLabel("Días")
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        dayLabels.forEach { (d, l) ->
                            FilterChip(
                                selected = d in days,
                                onClick = { days = if (d in days && days.size > 1) days - d else days + d },
                                label = { Text(l) },
                                colors = FilterChipDefaults.filterChipColors(selectedContainerColor = Ink.Yellow),
                            )
                        }
                    }
                }
                Mode.EVERY -> {
                    SectionLabel("Cada")
                    Stepper("$hours horas", onMinus = { hours = (hours - 1).coerceAtLeast(1) }, onPlus = { hours = (hours + 1).coerceAtMost(24) })
                    SectionLabel("Primera toma del día")
                    InputChip(selected = false, onClick = { picking = -2 }, label = { Text(firstDose.label()) })
                }
                Mode.CYCLIC -> {
                    SectionLabel("Días tomando")
                    Stepper("$daysOn días", onMinus = { daysOn = (daysOn - 1).coerceAtLeast(1) }, onPlus = { daysOn++ })
                    SectionLabel("Días de descanso")
                    Stepper("$daysOff días", onMinus = { daysOff = (daysOff - 1).coerceAtLeast(0) }, onPlus = { daysOff++ })
                }
                else -> Unit
            }

            if (mode in setOf(Mode.DAILY, Mode.DAYS, Mode.CYCLIC)) {
                SectionLabel("Horas")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    times.forEachIndexed { i, time ->
                        // Tap an hour to change it or delete it.
                        InputChip(selected = false, onClick = { picking = i }, label = { Text(time.label()) })
                    }
                    TextButton(onClick = { picking = -1 }) { Text("+ Hora") }
                }
            }

            if (mode != Mode.AS_NEEDED) {
                SectionLabel("Tipo de aviso")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = !strong, onClick = { strong = false }, label = { Text("Suave") },
                        colors = FilterChipDefaults.filterChipColors(selectedContainerColor = Ink.Blue, selectedLabelColor = Ink.Paper),
                    )
                    FilterChip(
                        selected = strong, onClick = { strong = true }, label = { Text("Fuerte") },
                        colors = FilterChipDefaults.filterChipColors(selectedContainerColor = Ink.Pink),
                    )
                }
                Text(
                    if (strong) "Alarma a pantalla completa que suena aunque el teléfono esté en silencio."
                    else "Una notificación normal.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Ink.Muted,
                    modifier = Modifier.padding(top = 4.dp),
                )
                ToggleRow("Repetir a los 30 min si no la registras", followUp) { followUp = it }
                ToggleRow("Tiene fecha de fin", hasEnd) { hasEnd = it }
                if (hasEnd) {
                    Stepper("Por $durationDays días", onMinus = { durationDays = (durationDays - 1).coerceAtLeast(1) }, onPlus = { durationDays++ })
                }
            }

            Spacer(Modifier.height(24.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                if (t != null) {
                    OutlinedButton(onClick = { onSave(null) }, modifier = Modifier.weight(1f)) { Text("Quitar") }
                }
                Button(
                    onClick = {
                        val schedule = when (mode) {
                            Mode.DAILY -> Schedule.Daily(times.sorted())
                            Mode.DAYS -> Schedule.SpecificDays(days, times.sorted())
                            Mode.EVERY -> Schedule.EveryHours(hours, firstDose)
                            Mode.CYCLIC -> Schedule.Cyclic(daysOn, daysOff, times.sorted())
                            Mode.AS_NEEDED -> Schedule.AsNeeded
                        }
                        // Keep the original start so cycles and history stay aligned.
                        val start = t?.startDate ?: today
                        onSave(
                            Treatment(
                                schedule = schedule,
                                startDate = start,
                                endDate = if (hasEnd && mode != Mode.AS_NEEDED) today.plus(DatePeriod(days = durationDays - 1)) else null,
                                reminderLevel = if (strong) ReminderLevel.STRONG else ReminderLevel.SOFT,
                                followUpMinutes = if (followUp) 30 else null,
                            ),
                        )
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Ink.Pink),
                    modifier = Modifier.weight(1f),
                ) { Text("Guardar") }
            }
        }
    }

    picking?.let { index ->
        val initial = when (index) {
            -2 -> firstDose
            -1 -> LocalTime(20, 0)
            else -> times[index]
        }
        val state = rememberTimePickerState(initialHour = initial.hour, initialMinute = initial.minute, is24Hour = true)
        AlertDialog(
            onDismissRequest = { picking = null },
            confirmButton = {
                TextButton(onClick = {
                    val chosen = LocalTime(state.hour, state.minute)
                    when (index) {
                        -2 -> firstDose = chosen
                        -1 -> times = (times + chosen).distinct()
                        else -> times = times.toMutableList().also { it[index] = chosen }.distinct()
                    }
                    picking = null
                }) { Text("Listo") }
            },
            dismissButton = {
                if (index >= 0 && times.size > 1) {
                    TextButton(onClick = { times = times.filterIndexed { i, _ -> i != index }; picking = null }) {
                        Text("Borrar hora")
                    }
                } else {
                    TextButton(onClick = { picking = null }) { Text("Cancelar") }
                }
            },
            text = { TimePicker(state = state) },
            containerColor = Ink.Paper,
        )
    }
}

@Composable
private fun Stepper(label: String, onMinus: () -> Unit, onPlus: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        OutlinedButton(onClick = onMinus) { Text("−") }
        Text(label, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(horizontal = 16.dp))
        OutlinedButton(onClick = onPlus) { Text("+") }
    }
}

@Composable
private fun ToggleRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange, colors = SwitchDefaults.colors(checkedTrackColor = Ink.Pink))
    }
}

/**
 * Shown on "Hoy" while something the reminders need is still off. Re-checks every time the
 * user comes back from Settings.
 */
@Composable
fun ReminderSetupCard(state: JournalState, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val scheduled = state.cabinet.filter { it.treatment != null && it.treatment?.schedule != Schedule.AsNeeded }
    if (scheduled.isEmpty()) return
    val anyStrong = scheduled.any { it.treatment?.reminderLevel == ReminderLevel.STRONG }

    var tick by remember { mutableIntStateOf(0) }
    LifecycleResumeEffect(Unit) {
        tick++
        onPauseOrDispose { }
    }
    val missing = remember(tick, anyStrong) { ReminderPermissions.missing(context, anyStrong) }
    val askNotifications = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { tick++ }
    if (missing.isEmpty()) return

    Column(
        modifier.fillMaxWidth().padding(top = 16.dp)
            .background(Ink.Yellow, RoundedCornerShape(20.dp)).padding(16.dp),
    ) {
        Text("Activa tus recordatorios", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        missing.forEach { item ->
            Row(Modifier.fillMaxWidth().padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(item.title, fontWeight = FontWeight.Bold)
                    Text(item.why, style = MaterialTheme.typography.bodySmall)
                }
                Spacer(Modifier.width(8.dp))
                TextButton(onClick = {
                    if (item == ReminderPermissions.Item.NOTIFICATIONS && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        askNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
                    } else {
                        context.startActivity(ReminderPermissions.settingsIntent(context, item))
                    }
                }) { Text("Activar", color = Ink.Blue, fontWeight = FontWeight.Bold) }
            }
        }
    }
}
