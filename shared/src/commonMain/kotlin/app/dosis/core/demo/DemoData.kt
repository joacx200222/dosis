package app.dosis.core.demo

import app.dosis.core.interactions.InMemoryInteractionDatabase
import app.dosis.core.interactions.InteractionEngine
import app.dosis.core.interactions.KnownInteraction
import app.dosis.core.interactions.Severity
import app.dosis.core.interactions.Substance
import app.dosis.core.journal.Journal
import app.dosis.core.journal.JournalState
import app.dosis.core.model.Dose
import app.dosis.core.model.DoseNote
import app.dosis.core.model.DoseStatus
import app.dosis.core.model.InkColor
import app.dosis.core.model.Medicine
import app.dosis.core.model.PillAppearance
import app.dosis.core.model.PillShape
import app.dosis.core.model.ReminderLevel
import app.dosis.core.model.Schedule
import app.dosis.core.model.Stock
import app.dosis.core.model.Treatment
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime

/**
 * DEMO JOURNAL — the medicines and doses from the Figma mockups, so the UIs have something to show.
 * The apps check them against the real bundled data (pass an engine to [journal]); the tiny
 * [database] below is only a test fixture, NOT clinical data.
 */
object DemoData {
    private const val DEMO = "Datos de prueba"

    val today = LocalDate(2026, 11, 8)

    val ibuprofeno = Medicine(
        "ibu", "Ibuprofeno", "400 mg", setOf("ibuprofen"),
        PillAppearance(PillShape.ROUND, InkColor.PINK),
        treatment = Treatment(Schedule.AsNeeded, today),
    )
    val loratadina = Medicine(
        "lora", "Loratadina", "10 mg", setOf("loratadine"),
        PillAppearance(PillShape.ROUND, InkColor.BLUE),
        treatment = Treatment(Schedule.Daily(listOf(LocalTime(15, 0))), LocalDate(2026, 10, 1)),
    )
    val omeprazol = Medicine(
        "ome", "Omeprazol", "20 mg", setOf("omeprazole"),
        PillAppearance(PillShape.CAPSULE, InkColor.BLUE, InkColor.YELLOW),
        treatment = Treatment(
            Schedule.Daily(listOf(LocalTime(8, 0), LocalTime(20, 0))),
            LocalDate(2026, 11, 8), LocalDate(2026, 11, 15), ReminderLevel.STRONG,
        ),
        stock = Stock(remaining = 4, alertAt = 5),
    )
    val naproxeno = Medicine(
        "napro", "Naproxeno", "550 mg", setOf("naproxen"),
        PillAppearance(PillShape.OVAL, InkColor.YELLOW),
        treatment = Treatment(Schedule.AsNeeded, today),
    )
    val paracetamol = Medicine(
        "para", "Paracetamol", "500 mg", setOf("acetaminophen"),
        PillAppearance(PillShape.ROUND, InkColor.BLUE), inCabinet = false,
    )

    val catalog = listOf(ibuprofeno, loratadina, omeprazol, naproxeno, paracetamol)

    val database = InMemoryInteractionDatabase(
        drugPairs = mapOf(
            ("ibuprofen" to "naproxen") to KnownInteraction(
                Severity.MODERATE, "Dos antiinflamatorios juntos: más riesgo estomacal y renal.", DEMO,
            ),
        ),
        substances = mapOf(
            ("ibuprofen" to Substance.ALCOHOL) to KnownInteraction(
                Severity.MAJOR, "Mayor riesgo de sangrado estomacal.", DEMO,
            ),
            ("ibuprofen" to Substance.COCAINE) to KnownInteraction(
                Severity.MAJOR, "Más carga para el corazón y los riñones.", DEMO,
            ),
            ("naproxen" to Substance.ALCOHOL) to KnownInteraction(
                Severity.MAJOR, "Mayor riesgo de sangrado estomacal.", DEMO,
            ),
            ("loratadine" to Substance.ALCOHOL) to KnownInteraction(
                Severity.MINOR, "Puede sumar algo de somnolencia.", DEMO,
            ),
        ),
    )

    fun journal(engine: InteractionEngine = InteractionEngine(database)): Journal {
        var counter = 0
        val doses = listOf(
            Dose("d1", "ome", LocalDateTime(2026, 11, 8, 8, 10), DoseStatus.TAKEN, note = DoseNote(text = "Antes de desayunar.")),
            Dose("d2", "ibu", LocalDateTime(2026, 11, 8, 13, 30), DoseStatus.TAKEN, note = DoseNote(setOf("Me sentí mejor"))),
            Dose("d3", "lora", LocalDateTime(2026, 11, 8, 15, 0), DoseStatus.SKIPPED, note = DoseNote(text = "Hoy no la necesité.")),
        )
        val state = JournalState(
            medicines = catalog.associateBy { it.id },
            doses = doses,
        )
        return Journal(engine, state) { "n${++counter}" }
    }
}
