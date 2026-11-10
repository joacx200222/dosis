package app.dosis.core.schedule

import app.dosis.core.model.Medicine
import app.dosis.core.model.Schedule
import app.dosis.core.model.Treatment
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.atTime
import kotlinx.datetime.daysUntil
import kotlinx.datetime.plus

data class ScheduledDose(val medicine: Medicine, val at: LocalDateTime)

object ScheduleCalculator {

    /** Planned times for [treatment] on [date], sorted. Empty outside start/end or for AsNeeded. */
    fun timesOn(treatment: Treatment, date: LocalDate): List<LocalTime> {
        if (date < treatment.startDate) return emptyList()
        treatment.endDate?.let { if (date > it) return emptyList() }
        return when (val s = treatment.schedule) {
            is Schedule.Daily -> s.times
            is Schedule.SpecificDays -> if (date.dayOfWeek in s.days) s.times else emptyList()
            is Schedule.EveryHours -> generateSequence(s.firstDose.toSecondOfDay()) { it + s.hours * 3600 }
                .takeWhile { it < 24 * 3600 }
                .map { LocalTime.fromSecondOfDay(it) }
                .toList()
            is Schedule.Cyclic -> {
                val day = treatment.startDate.daysUntil(date)
                if (day % (s.daysOn + s.daysOff) < s.daysOn) s.times else emptyList()
            }
            Schedule.AsNeeded -> emptyList()
        }.sorted()
    }

    /** Next planned dose strictly after [now] across all medicines, looking up to [horizonDays] ahead. */
    fun nextDose(medicines: Collection<Medicine>, now: LocalDateTime, horizonDays: Int = 60): ScheduledDose? {
        for (offset in 0..horizonDays) {
            val date = now.date.plus(DatePeriod(days = offset))
            val candidates = medicines.flatMap { med ->
                val t = med.treatment ?: return@flatMap emptyList()
                timesOn(t, date).map { ScheduledDose(med, date.atTime(it)) }
            }.filter { it.at > now }
            candidates.minByOrNull { it.at }?.let { return it }
        }
        return null
    }
}
