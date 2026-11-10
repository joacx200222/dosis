package app.dosis.wear

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material3.AppScaffold
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.Text
import app.dosis.core.data.InteractionDataParser
import app.dosis.core.data.InteractionDataStore
import app.dosis.core.demo.DemoData
import app.dosis.core.interactions.InteractionEngine
import app.dosis.core.interactions.InteractionReport
import app.dosis.core.interactions.Severity
import app.dosis.core.journal.Journal
import app.dosis.core.model.DoseStatus
import app.dosis.core.model.Medicine
import app.dosis.core.model.PillAppearance
import app.dosis.core.model.PillShape
import app.dosis.core.time.hhmm
import app.dosis.core.time.nowLocal
import app.dosis.core.ui.DosisPalette
import kotlin.concurrent.thread

/** Local copy of the journal. The Wear Data Layer sync with the phone plugs in here. */
object WearGraph {
    /** Same bundled interaction file as the phone (assets/interactions.tsv), parsed off the main thread. */
    val interactionData = InteractionDataStore()
    val journal by lazy { DemoData.journal(InteractionEngine(interactionData)) }

    @Volatile private var loading = false

    fun loadInteractionData(context: Context) {
        if (loading) return
        loading = true
        val app = context.applicationContext
        thread(name = "dosis-interactions", isDaemon = true) {
            val text = app.assets.open("interactions.tsv").bufferedReader().use { it.readText() }
            interactionData.set(InteractionDataParser.parse(text))
        }
    }
}

private val WatchBg = Color(DosisPalette.WatchBackground)
private val Pink = Color(DosisPalette.Pink)
private val Blue = Color(DosisPalette.Blue)
private val Yellow = Color(DosisPalette.Yellow)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WearGraph.loadInteractionData(this)
        setContent { WearApp(WearGraph.journal) }
    }
}

private sealed interface WearScreen {
    data object Register : WearScreen
    data class Logged(val report: InteractionReport) : WearScreen
}

@Composable
fun WearApp(journal: Journal) {
    var screen by remember { mutableStateOf<WearScreen>(WearScreen.Register) }
    AppScaffold(modifier = Modifier.background(WatchBg)) {
        when (val s = screen) {
            WearScreen.Register -> RegisterScreen(journal) { med ->
                val report = journal.preview(med)
                journal.logDose(med, nowLocal(), addToCabinet = true)
                screen = WearScreen.Logged(report)
            }
            is WearScreen.Logged -> LoggedScreen(s.report) { screen = WearScreen.Register }
        }
    }
}

@Composable
private fun RegisterScreen(journal: Journal, onLog: (Medicine) -> Unit) {
    val state by journal.state.collectAsState()
    val listState = rememberScalingLazyListState()
    val recent = state.recent(3).ifEmpty { state.cabinet.take(3) }
    val next = journal.nextDose(nowLocal())

    ScreenScaffold(scrollState = listState) {
        ScalingLazyColumn(state = listState, modifier = Modifier.fillMaxSize().background(WatchBg)) {
            item { Text("Registrar toma", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color.White) }
            recent.forEachIndexed { i, med ->
                item {
                    Button(
                        onClick = { onLog(med) },
                        modifier = Modifier.fillMaxWidth(),
                        colors = if (i == 0) ButtonDefaults.buttonColors(containerColor = Pink)
                        else ButtonDefaults.filledTonalButtonColors(),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            WatchPill(med.appearance, 16.dp)
                            Spacer(Modifier.width(8.dp))
                            Text(med.displayName, color = Color.White)
                        }
                    }
                }
            }
            next?.let {
                item {
                    Text("Próxima: ${it.medicine.name} ${it.at.hhmm()}", color = Yellow, fontSize = 12.sp)
                }
            }
            item { Text("Hoy", fontWeight = FontWeight.Bold, color = Color.White, modifier = Modifier.padding(top = 8.dp)) }
            state.dosesOn(nowLocal().date).forEach { d ->
                item {
                    val m = state.medicines[d.medicineId]
                    Text(
                        "${d.at.hhmm()}  ${m?.name ?: "?"}  ${if (d.status == DoseStatus.TAKEN) "✓" else "—"}",
                        color = Color.White,
                        fontSize = 12.sp,
                    )
                }
            }
        }
    }
}

/** Short warning list; full detail lives on the phone. */
@Composable
private fun LoggedScreen(report: InteractionReport, onDone: () -> Unit) {
    val listState = rememberScalingLazyListState()
    val majors = report.alwaysConsider.filter { it.severity == Severity.MAJOR }.map { it.substance.label }
    val moderates = report.alwaysConsider.filter { it.severity == Severity.MODERATE && it.hasData }.map { it.substance.label }
    val noData = report.alwaysConsider.count { !it.hasData }

    ScreenScaffold(scrollState = listState) {
        ScalingLazyColumn(state = listState, modifier = Modifier.fillMaxSize().background(WatchBg)) {
            item {
                Text(
                    "✓ ${report.medicine.name} registrado",
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    textAlign = TextAlign.Center,
                )
            }
            report.withCabinet.forEach { w ->
                item { WarningBar(w.interaction.severity, "${w.other.name} (botiquín)") }
            }
            if (majors.isNotEmpty()) item { WarningBar(Severity.MAJOR, majors.joinToString(" / ")) }
            if (moderates.isNotEmpty()) item { WarningBar(Severity.MODERATE, moderates.joinToString(" / ")) }
            if (noData > 0) item { WarningBar(Severity.MODERATE, "$noData más sin datos") }
            if (!report.dataReady) item { Text("Cargando datos…", color = Color.White, fontSize = 11.sp) }
            item { Text("Ver todo en el teléfono →", color = Blue, fontSize = 12.sp) }
            item { Button(onClick = onDone, modifier = Modifier.fillMaxWidth()) { Text("Listo") } }
        }
    }
}

@Composable
private fun WarningBar(severity: Severity, text: String) {
    val bg = Color(DosisPalette.severity(severity))
    Text(
        "${severity.symbol} ${severity.label} · $text",
        color = Color.Black,
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth().background(bg, RoundedCornerShape(50)).padding(horizontal = 8.dp, vertical = 4.dp),
    )
}

@Composable
private fun WatchPill(appearance: PillAppearance, height: Dp) {
    val a = Color(DosisPalette.ink(appearance.primary))
    val b = Color(DosisPalette.ink(appearance.secondary ?: appearance.primary))
    val w = if (appearance.shape == PillShape.CAPSULE || appearance.shape == PillShape.OVAL) height * 2 else height
    Canvas(Modifier.size(w, height)) {
        val r = CornerRadius(size.height / 2)
        clipRect(right = size.width / 2) { drawRoundRect(a, cornerRadius = r) }
        clipRect(left = size.width / 2) { drawRoundRect(b, cornerRadius = r) }
    }
}
