package app.dosis.web

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.ComposeViewport
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import app.dosis.core.data.InteractionDataParser
import app.dosis.core.data.InteractionDataStore
import app.dosis.core.demo.DemoData
import app.dosis.core.interactions.InteractionEngine
import app.dosis.core.interactions.InteractionReport
import app.dosis.core.interactions.Severity
import app.dosis.core.journal.Journal
import app.dosis.core.journal.JournalState
import app.dosis.core.model.DoseStatus
import app.dosis.core.model.Medicine
import app.dosis.core.time.hhmm
import app.dosis.core.time.monthName
import app.dosis.core.time.nowLocal
import app.dosis.core.ui.DosisPalette
import kotlinx.browser.document
import kotlinx.browser.window
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.await
import kotlinx.coroutines.launch
import org.w3c.fetch.Response
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus
import kotlinx.datetime.plus

private val Blue = Color(DosisPalette.Blue)
private val Pink = Color(DosisPalette.Pink)
private val Muted = Color(DosisPalette.InkMuted)
private val Subtle = Color(DosisPalette.PaperSubtle)

@OptIn(ExperimentalComposeUiApi::class)
fun main() {
    // Supabase sync (decrypting in the browser) replaces the demo journal later.
    val interactionData = InteractionDataStore()
    val journal = DemoData.journal(InteractionEngine(interactionData))
    MainScope().launch {
        // Bundled DDInter + TripSit + dosis tables, served next to index.html.
        val response = window.fetch("interactions.tsv").await<Response>()
        val text = response.text().await<JsString>().toString()
        interactionData.set(InteractionDataParser.parse(text))
    }
    ComposeViewport(document.body!!) {
        MaterialTheme(colorScheme = lightColorScheme(primary = Blue, secondary = Pink, background = Color.White, surface = Color.White)) {
            WebApp(journal)
        }
    }
}

private enum class WebTab(val label: String) { HISTORIAL("Historial"), BOTIQUIN("Botiquín") }

@Composable
fun WebApp(journal: Journal) {
    val state by journal.state.collectAsState()
    var tab by remember { mutableStateOf(WebTab.HISTORIAL) }
    var registering by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().background(Color.White)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 40.dp, vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("dosis", color = Blue, fontSize = 30.sp, fontWeight = FontWeight.Black)
            Spacer(Modifier.width(40.dp))
            WebTab.entries.forEach { t ->
                Text(
                    t.label,
                    color = if (tab == t) Color.Black else Blue,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .padding(end = 12.dp)
                        .background(if (tab == t) Pink else Color.Transparent, RoundedCornerShape(50))
                        .clickable { tab = t }
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
            Spacer(Modifier.weight(1f))
            Button(onClick = { registering = true }) { Text("+ Registrar toma") }
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(Subtle))
        Row(Modifier.fillMaxSize().padding(40.dp).verticalScroll(rememberScrollState())) {
            when (tab) {
                WebTab.HISTORIAL -> Historial(state)
                WebTab.BOTIQUIN -> Botiquin(journal, state)
            }
        }
    }

    if (registering) RegisterDialog(journal, state) { registering = false }
}

/** Home on web. No interaction warnings here — by design. */
@Composable
private fun Historial(state: JournalState) {
    val today = remember { nowLocal().date }
    var selected by remember { mutableStateOf(today) }
    Row(horizontalArrangement = Arrangement.spacedBy(64.dp)) {
        Column(Modifier.widthIn(max = 520.dp)) {
            Text("Tu historial.", fontSize = 44.sp, fontWeight = FontWeight.Black)
            Text("${monthName(selected.month.ordinal)} ${selected.year}", fontSize = 22.sp, modifier = Modifier.padding(vertical = 16.dp))
            MonthGrid(state, selected, today) { selected = it }
        }
        Column(Modifier.widthIn(max = 520.dp)) {
            Label("${selected.day} de ${monthName(selected.month.ordinal).lowercase()}")
            val doses = state.dosesOn(selected)
            if (doses.isEmpty()) Text("Nada registrado este día.", color = Muted)
            doses.forEach { d ->
                val m = state.medicines[d.medicineId] ?: return@forEach
                Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(d.at.hhmm(), color = Muted, modifier = Modifier.width(56.dp))
                    Dot(m)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(m.displayName, fontSize = 18.sp)
                        (d.note?.text ?: d.note?.chips?.firstOrNull())?.let { Text(it, color = Muted, fontSize = 13.sp) }
                    }
                    Text(if (d.status == DoseStatus.TAKEN) "✓ Tomada" else "— Omitida", color = if (d.status == DoseStatus.TAKEN) Blue else Muted)
                }
            }
        }
    }
}

@Composable
private fun MonthGrid(state: JournalState, selected: LocalDate, today: LocalDate, onSelect: (LocalDate) -> Unit) {
    val first = LocalDate(selected.year, selected.month, 1)
    val days = first.plus(DatePeriod(months = 1)).minus(DatePeriod(days = 1)).day
    val cells = List(first.dayOfWeek.ordinal) { null } + (1..days).map { LocalDate(selected.year, selected.month, it) }
    Row { listOf("L", "M", "X", "J", "V", "S", "D").forEach { Text(it, color = Blue, modifier = Modifier.width(64.dp)) } }
    cells.chunked(7).forEach { week ->
        Row(Modifier.padding(vertical = 6.dp)) {
            week.forEach { date ->
                Box(Modifier.width(64.dp), contentAlignment = Alignment.CenterStart) {
                    if (date != null) {
                        val sel = date == selected
                        val marks = state.dosesOn(date)
                        Box(
                            Modifier.size(44.dp).background(if (sel) Pink else Color.Transparent, CircleShape).clickable { onSelect(date) },
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                "${date.day}" + if (marks.isNotEmpty()) "\n·" else "",
                                color = if (sel) Color.White else if (date > today) Muted else Color.Black,
                                fontSize = 15.sp,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Botiquin(journal: Journal, state: JournalState) {
    Column(Modifier.widthIn(max = 900.dp)) {
        Text("Tu botiquín.", fontSize = 44.sp, fontWeight = FontWeight.Black)
        Spacer(Modifier.height(16.dp))
        state.cabinet.forEach { m ->
            Row(Modifier.padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Dot(m)
                Spacer(Modifier.width(16.dp))
                Column {
                    Text(m.displayName, fontSize = 20.sp, fontWeight = FontWeight.Black)
                    m.stock?.let { Text("Quedan ${it.remaining}", color = Blue, fontSize = 13.sp) }
                }
            }
        }
        if (state.oneOffs.isNotEmpty()) {
            Label("Tomadas alguna vez")
            state.oneOffs.forEach { m ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(m.displayName)
                    TextButton(onClick = { journal.setInCabinet(m.id, true) }) { Text("+ Agregar") }
                }
            }
        }
    }
}

/** Warnings shown BEFORE confirming, same rule as phone and tablet. */
@Composable
private fun RegisterDialog(journal: Journal, state: JournalState, onClose: () -> Unit) {
    var query by remember { mutableStateOf("") }
    var picked by remember { mutableStateOf<Medicine?>(null) }
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            Modifier.fillMaxWidth(0.92f).widthIn(max = 760.dp).background(Color.White, RoundedCornerShape(24.dp)).padding(32.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            Text("¿Qué tomaste?", fontSize = 30.sp, fontWeight = FontWeight.Black)
            Spacer(Modifier.height(16.dp))
            val med = picked
            if (med == null) {
                OutlinedTextField(query, { query = it }, placeholder = { Text("Busca una medicina") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                journal.suggest(query).forEach { m ->
                    Text(m.displayName, modifier = Modifier.fillMaxWidth().clickable { picked = m }.padding(vertical = 10.dp))
                }
                if (query.isNotBlank()) {
                    Text("+ Registrar \"${query.trim()}\" como nueva medicina", color = Blue,
                        modifier = Modifier.clickable { picked = journal.resolve(query) }.padding(vertical = 10.dp))
                }
            } else {
                Text(med.displayName, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                val data by journal.interactionData.collectAsState()
                Warnings(remember(med, data) { journal.preview(med) })
                Spacer(Modifier.height(20.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(onClick = onClose) { Text("Cancelar") }
                    Button(onClick = { journal.logDose(med, nowLocal(), addToCabinet = true); onClose() }) { Text("Registrar igual") }
                }
            }
        }
    }
}

@Composable
private fun Warnings(report: InteractionReport) {
    if (!report.dataReady) Text("Cargando datos de interacciones…", color = Muted, fontSize = 13.sp, modifier = Modifier.padding(top = 12.dp))
    if (report.withCabinet.isNotEmpty()) {
        Label("Con tu botiquín")
        report.withCabinet.forEach { w ->
            Row(Modifier.padding(top = 6.dp), verticalAlignment = Alignment.Top) {
                Tag(w.interaction.severity)
                Column(Modifier.padding(start = 10.dp)) {
                    Text("${report.medicine.name} + ${w.other.name}", fontWeight = FontWeight.Bold)
                    Text("${w.interaction.description} ${w.other.name} está en tu botiquín.", color = Muted, fontSize = 13.sp)
                }
            }
        }
    }
    Label("Ten en cuenta siempre")
    if (report.noSubstanceData) {
        Text("Sin datos específicos para esta medicina. Por precaución, considéralo al menos moderado.", color = Muted, fontSize = 13.sp)
    }
    report.alwaysByGroup.forEach { (group, warnings) ->
        Text(group.label, fontWeight = FontWeight.Black, fontSize = 15.sp, modifier = Modifier.padding(top = 14.dp))
        warnings.filter { it.hasData }.forEach { w ->
            Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.Top) {
                Tag(w.severity)
                Column(Modifier.padding(start = 10.dp)) {
                    Text(w.substance.label, fontWeight = FontWeight.Bold)
                    Text(w.description ?: "Consulta a tu farmacéutico.", color = Muted, fontSize = 13.sp)
                }
            }
        }
        val noData = warnings.filterNot { it.hasData }
        if (noData.isNotEmpty()) {
            Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.Top) {
                Tag(Severity.MODERATE)
                Column(Modifier.padding(start = 10.dp)) {
                    Text(noData.joinToString(" · ") { it.substance.label }, fontWeight = FontWeight.Bold)
                    if (!report.noSubstanceData) {
                        Text("Sin datos específicos. Por precaución, considéralas al menos moderadas.", color = Muted, fontSize = 13.sp)
                    }
                }
            }
        }
    }
    if (report.sources.isNotEmpty()) {
        Text("Fuente: ${report.sources.joinToString(" · ")}", color = Muted, fontSize = 12.sp, modifier = Modifier.padding(top = 14.dp))
    }
}

@Composable
private fun Tag(s: Severity) {
    Text(
        "${s.symbol} ${s.label}".trim(),
        fontSize = 12.sp,
        fontWeight = FontWeight.Bold,
        color = if (s == Severity.MAJOR) Color.White else Color.Black,
        modifier = Modifier.background(Color(DosisPalette.severity(s)), RoundedCornerShape(50)).padding(horizontal = 8.dp, vertical = 2.dp),
    )
}

@Composable
private fun Label(text: String) {
    Text(text.uppercase(), color = Blue, fontSize = 11.sp, letterSpacing = 1.5.sp, modifier = Modifier.padding(top = 20.dp, bottom = 8.dp))
}

@Composable
private fun Dot(m: Medicine) {
    Box(Modifier.size(24.dp).background(Color(DosisPalette.ink(m.appearance.primary)), CircleShape))
}
