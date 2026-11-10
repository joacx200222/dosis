package app.dosis.core

import app.dosis.core.demo.DemoData
import app.dosis.core.journal.JournalCodec
import app.dosis.core.journal.JournalState
import app.dosis.core.model.Dose
import app.dosis.core.model.DoseStatus
import app.dosis.core.model.Medicine
import app.dosis.core.model.ReminderLevel
import app.dosis.core.model.Schedule
import app.dosis.core.model.Treatment
import app.dosis.core.schedule.ReminderKind
import app.dosis.core.schedule.ReminderPlanner
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RemindersTest {
    private val start = LocalDate(2026, 11, 1)
    private val med = Medicine(
        "m", "Omeprazol",
        treatment = Treatment(Schedule.Daily(listOf(LocalTime(8, 0), LocalTime(20, 0))), start, followUpMinutes = 30),
    )

    private fun state(vararg doses: Dose) = JournalState(mapOf(med.id to med), doses.toList())

    @Test
    fun nextBatchIsTheNextPlannedDose() {
        val (at, batch) = ReminderPlanner.nextBatch(state(), LocalDateTime(2026, 11, 8, 9, 0))!!
        assertEquals(LocalDateTime(2026, 11, 8, 20, 0), at)
        assertEquals(listOf(ReminderKind.DUE), batch.map { it.kind })
    }

    @Test
    fun followUpOnlyWhileNotLogged() {
        val now = LocalDateTime(2026, 11, 8, 8, 10)
        val missed = ReminderPlanner.nextBatch(state(), now)!!
        assertEquals(LocalDateTime(2026, 11, 8, 8, 30), missed.first)
        assertEquals(ReminderKind.FOLLOW_UP, missed.second.single().kind)

        val taken = state(Dose("d", "m", LocalDateTime(2026, 11, 8, 8, 5), DoseStatus.TAKEN))
        assertEquals(LocalDateTime(2026, 11, 8, 20, 0), ReminderPlanner.nextBatch(taken, now)!!.first)
    }

    @Test
    fun skippingAlsoSilencesTheFollowUp() {
        val skipped = state(Dose("d", "m", LocalDateTime(2026, 11, 8, 8, 1), DoseStatus.SKIPPED))
        assertTrue(ReminderPlanner.isHandled(skipped, "m", LocalDateTime(2026, 11, 8, 8, 0)))
        assertFalse(ReminderPlanner.isHandled(skipped, "m", LocalDateTime(2026, 11, 8, 20, 0)))
    }

    @Test
    fun earlyDoseCountsButNotTooEarly() {
        val early = state(Dose("d", "m", LocalDateTime(2026, 11, 8, 7, 0), DoseStatus.TAKEN))
        assertTrue(ReminderPlanner.isHandled(early, "m", LocalDateTime(2026, 11, 8, 8, 0)))
        val tooEarly = state(Dose("d", "m", LocalDateTime(2026, 11, 8, 5, 0), DoseStatus.TAKEN))
        assertFalse(ReminderPlanner.isHandled(tooEarly, "m", LocalDateTime(2026, 11, 8, 8, 0)))
    }

    @Test
    fun windowShrinksForFrequentSchedules() {
        val every2 = med.copy(treatment = Treatment(Schedule.EveryHours(2, LocalTime(8, 0)), start))
        val s = JournalState(mapOf("m" to every2), listOf(Dose("d", "m", LocalDateTime(2026, 11, 8, 8, 0), DoseStatus.TAKEN)))
        assertTrue(ReminderPlanner.isHandled(s, "m", LocalDateTime(2026, 11, 8, 8, 0)))
        assertFalse(ReminderPlanner.isHandled(s, "m", LocalDateTime(2026, 11, 8, 10, 0)))
    }

    @Test
    fun ignoresAsNeededAndMedicinesOutsideCabinet() {
        val asNeeded = med.copy(id = "a", treatment = Treatment(Schedule.AsNeeded, start))
        val outside = med.copy(id = "o", inCabinet = false)
        val s = JournalState(mapOf("a" to asNeeded, "o" to outside))
        assertEquals(null, ReminderPlanner.nextBatch(s, LocalDateTime(2026, 11, 8, 9, 0)))
    }

    @Test
    fun sameMinuteRemindersComeTogether() {
        val other = med.copy(id = "z", name = "Loratadina", treatment = med.treatment!!.copy(reminderLevel = ReminderLevel.STRONG))
        val s = JournalState(mapOf(med.id to med, other.id to other))
        val (_, batch) = ReminderPlanner.nextBatch(s, LocalDateTime(2026, 11, 8, 9, 0))!!
        assertEquals(setOf("m", "z"), batch.map { it.medicineId }.toSet())
    }

    @Test
    fun journalRoundTripsThroughJson() {
        val days = med.copy(id = "d", treatment = Treatment(Schedule.SpecificDays(setOf(DayOfWeek.MONDAY), listOf(LocalTime(9, 0))), start))
        val s = DemoData.journal().state.value.let { it.copy(medicines = it.medicines + (days.id to days)) }
        val back = JournalCodec.decode(JournalCodec.encode(s))
        assertEquals(s.medicines, back.medicines)
        assertEquals(s.doses, back.doses)
    }
}
