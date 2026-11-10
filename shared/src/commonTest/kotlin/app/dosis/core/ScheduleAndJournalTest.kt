package app.dosis.core

import app.dosis.core.demo.DemoData
import app.dosis.core.model.Medicine
import app.dosis.core.model.Schedule
import app.dosis.core.model.Treatment
import app.dosis.core.schedule.ScheduleCalculator
import app.dosis.core.search.MedicineSuggester
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ScheduleAndJournalTest {
    private val start = LocalDate(2026, 11, 1) // Sunday

    @Test
    fun dailyRespectsStartAndEnd() {
        val t = Treatment(Schedule.Daily(listOf(LocalTime(20, 0), LocalTime(8, 0))), start, LocalDate(2026, 11, 3))
        assertEquals(listOf(LocalTime(8, 0), LocalTime(20, 0)), ScheduleCalculator.timesOn(t, LocalDate(2026, 11, 2)))
        assertTrue(ScheduleCalculator.timesOn(t, LocalDate(2026, 10, 31)).isEmpty())
        assertTrue(ScheduleCalculator.timesOn(t, LocalDate(2026, 11, 4)).isEmpty())
    }

    @Test
    fun specificDays() {
        val t = Treatment(Schedule.SpecificDays(setOf(DayOfWeek.MONDAY), listOf(LocalTime(9, 0))), start)
        assertEquals(1, ScheduleCalculator.timesOn(t, LocalDate(2026, 11, 2)).size) // Monday
        assertTrue(ScheduleCalculator.timesOn(t, LocalDate(2026, 11, 3)).isEmpty())
    }

    @Test
    fun everyEightHours() {
        val t = Treatment(Schedule.EveryHours(8, LocalTime(6, 0)), start)
        assertEquals(
            listOf(LocalTime(6, 0), LocalTime(14, 0), LocalTime(22, 0)),
            ScheduleCalculator.timesOn(t, start),
        )
    }

    @Test
    fun cyclicTwentyOneOnSevenOff() {
        val t = Treatment(Schedule.Cyclic(21, 7, listOf(LocalTime(9, 0))), start)
        assertFalse(ScheduleCalculator.timesOn(t, LocalDate(2026, 11, 21)).isEmpty()) // day 20
        assertTrue(ScheduleCalculator.timesOn(t, LocalDate(2026, 11, 22)).isEmpty()) // day 21 = rest
        assertFalse(ScheduleCalculator.timesOn(t, LocalDate(2026, 11, 29)).isEmpty()) // day 28 = new cycle
    }

    @Test
    fun nextDoseFromDemo() {
        val journal = DemoData.journal()
        val next = journal.nextDose(LocalDateTime(2026, 11, 8, 15, 30))
        assertEquals("ome", next?.medicine?.id)
        assertEquals(LocalDateTime(2026, 11, 8, 20, 0), next?.at)
    }

    @Test
    fun logFreeTypedMedicineWithoutPreRegistering() {
        val journal = DemoData.journal()
        val med = journal.resolve("Clonazepam", "0.5 mg")
        journal.logDose(med, LocalDateTime(2026, 11, 8, 22, 0), addToCabinet = true)
        val s = journal.state.value
        assertTrue(s.cabinet.any { it.name == "Clonazepam" })
        assertEquals("Clonazepam", s.recent(1).single().name)
    }

    @Test
    fun takingADoseLowersStock() {
        val journal = DemoData.journal()
        journal.logDose(DemoData.omeprazol, LocalDateTime(2026, 11, 8, 20, 0), addToCabinet = true)
        assertEquals(3, journal.state.value.medicines.getValue("ome").stock?.remaining)
    }

    @Test
    fun suggesterIsAccentInsensitiveAndRanksRecentFirst() {
        val recent = listOf(DemoData.ibuprofeno)
        val r = MedicineSuggester.suggest("PARAC", recent, DemoData.catalog)
        assertEquals("Paracetamol", r.first().name)
        val all = MedicineSuggester.suggest("", recent, DemoData.catalog)
        assertEquals(listOf<Medicine>(DemoData.ibuprofeno), all)
    }

    @Test
    fun demoPreviewMatchesMockup() {
        val journal = DemoData.journal()
        val report = journal.preview(DemoData.ibuprofeno)
        assertEquals(listOf("napro"), report.withCabinet.map { it.other.id })
    }
}
