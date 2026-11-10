package app.dosis.core.schedule

import app.dosis.core.journal.JournalState
import app.dosis.core.model.Medicine
import app.dosis.core.model.ReminderLevel
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atTime
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.ExperimentalTime

enum class ReminderKind {
    /** At the planned time. */
    DUE,

    /** [app.dosis.core.model.Treatment.followUpMinutes] later, only if the dose is still not logged. */
    FOLLOW_UP,
}

/** One notification to show at [fireAt] for the dose planned at [scheduledAt]. */
data class ReminderEvent(
    val medicineId: String,
    val scheduledAt: LocalDateTime,
    val fireAt: LocalDateTime,
    val kind: ReminderKind,
    val level: ReminderLevel,
)

/**
 * Decides which reminders are pending. Platform code only has to set one alarm for
 * [nextBatch] and call it again whenever the journal changes or an alarm fires.
 */
@OptIn(ExperimentalTime::class)
object ReminderPlanner {

    /** A dose logged this close to the planned time (taken or skipped) counts as "handled". */
    private val maxBefore = 90.minutes
    private val maxAfter = 6.hours

    /** Planned doses of [medicine] in [from]..[to] (inclusive), using the local calendar. */
    fun plannedBetween(medicine: Medicine, from: LocalDateTime, to: LocalDateTime): List<LocalDateTime> {
        val t = medicine.treatment ?: return emptyList()
        val out = ArrayList<LocalDateTime>()
        var date = from.date
        while (date <= to.date) {
            ScheduleCalculator.timesOn(t, date).forEach { time ->
                val at = date.atTime(time)
                if (at in from..to) out += at
            }
            date = date.plus(DatePeriod(days = 1))
        }
        return out
    }

    /** True when a dose of [medicineId] was logged in the window around [scheduledAt]. */
    fun isHandled(state: JournalState, medicineId: String, scheduledAt: LocalDateTime): Boolean {
        val med = state.medicines[medicineId] ?: return true
        val (before, after) = window(med, scheduledAt)
        val start = scheduledAt.shift(-before)
        val end = scheduledAt.shift(after)
        return state.doses.any { it.medicineId == medicineId && it.at >= start && it.at <= end }
    }

    /** Pending reminders with fireAt strictly after [now], sorted, up to [horizonDays] ahead. */
    fun events(state: JournalState, now: LocalDateTime, horizonDays: Int = 2): List<ReminderEvent> {
        // Look back a day so a follow-up for a dose planned before `now` is still produced.
        val from = LocalDateTime(now.date.minus(DatePeriod(days = 1)), kotlinx.datetime.LocalTime(0, 0))
        val to = LocalDateTime(now.date.plus(DatePeriod(days = horizonDays)), kotlinx.datetime.LocalTime(23, 59))
        val out = ArrayList<ReminderEvent>()
        for (med in state.cabinet) {
            val t = med.treatment ?: continue
            for (at in plannedBetween(med, from, to)) {
                if (isHandled(state, med.id, at)) continue
                if (at > now) out += ReminderEvent(med.id, at, at, ReminderKind.DUE, t.reminderLevel)
                t.followUpMinutes?.takeIf { it > 0 }?.let { mins ->
                    val follow = at.shift(mins.minutes)
                    if (follow > now) out += ReminderEvent(med.id, at, follow, ReminderKind.FOLLOW_UP, t.reminderLevel)
                }
            }
        }
        return out.sortedWith(compareBy({ it.fireAt }, { it.medicineId }))
    }

    /** The earliest fire time and every reminder due at that same minute, or null if none. */
    fun nextBatch(state: JournalState, now: LocalDateTime): Pair<LocalDateTime, List<ReminderEvent>>? {
        val all = events(state, now)
        val first = all.firstOrNull() ?: return null
        return first.fireAt to all.filter { it.fireAt == first.fireAt }
    }

    /** Window is capped at half the gap to the neighbouring planned doses (e.g. every-2-hours schedules). */
    private fun window(med: Medicine, at: LocalDateTime): Pair<Duration, Duration> {
        val around = plannedBetween(med, at.shift(-24.hours), at.shift(24.hours))
        val prev = around.lastOrNull { it < at }
        val next = around.firstOrNull { it > at }
        val before = prev?.let { minOf(maxBefore, gap(it, at) / 2) } ?: maxBefore
        val after = next?.let { minOf(maxAfter, gap(at, it) / 2) } ?: maxAfter
        return before to after
    }

    private val tz = TimeZone.UTC // pure arithmetic on wall-clock times; no DST surprises

    private fun LocalDateTime.shift(d: Duration): LocalDateTime =
        (toInstant(tz) + d).toLocalDateTime(tz)

    private fun gap(a: LocalDateTime, b: LocalDateTime): Duration = b.toInstant(tz) - a.toInstant(tz)
}
