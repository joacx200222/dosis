package app.dosis.core.model

import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.serializers.DayOfWeekSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Riso inks available for drawing a pill. */
@Serializable
enum class InkColor { PINK, BLUE, YELLOW, BLACK, GRAY }

@Serializable
enum class PillShape { ROUND, OVAL, CAPSULE, LIQUID, INJECTION, DROPS }

@Serializable
data class PillAppearance(
    val shape: PillShape,
    val primary: InkColor,
    /** Second half of a capsule; ignored for other shapes. */
    val secondary: InkColor? = null,
)

/**
 * A medicine the user has taken or keeps.
 *
 * Nothing has to be created up front: logging a free-typed name creates a
 * [Medicine] on the fly. [inCabinet] decides whether it takes part in
 * "Con tu botiquín" interaction checks.
 */
@Serializable
data class Medicine(
    val id: String,
    val name: String,
    val strength: String? = null,
    /** Normalized active ingredients (see [app.dosis.core.text.normalizeName]). Empty = unknown. */
    val ingredients: Set<String> = emptySet(),
    val appearance: PillAppearance = PillAppearance(PillShape.ROUND, InkColor.BLUE),
    val inCabinet: Boolean = true,
    val treatment: Treatment? = null,
    val stock: Stock? = null,
) {
    val displayName: String get() = if (strength.isNullOrBlank()) name else "$name $strength"
}

@Serializable
data class Stock(val remaining: Int, val alertAt: Int) {
    val isLow: Boolean get() = remaining <= alertAt
}

@Serializable
enum class ReminderLevel {
    /** Regular notification. */
    SOFT,

    /** Full-screen alert that sounds even in Do Not Disturb. */
    STRONG,
}

@Serializable
data class Treatment(
    val schedule: Schedule,
    val startDate: LocalDate,
    val endDate: LocalDate? = null,
    val reminderLevel: ReminderLevel = ReminderLevel.SOFT,
    /** Minutes after a missed dose to remind again; null = no follow-up. */
    val followUpMinutes: Int? = 30,
)

@Serializable
sealed interface Schedule {
    @Serializable @SerialName("daily")
    data class Daily(val times: List<LocalTime>) : Schedule
    @Serializable @SerialName("days")
    data class SpecificDays(val days: Set<@Serializable(with = DayOfWeekSerializer::class) DayOfWeek>, val times: List<LocalTime>) : Schedule

    @Serializable @SerialName("every_hours")
    /** Every [hours] hours starting at [firstDose] each day. */
    data class EveryHours(val hours: Int, val firstDose: LocalTime) : Schedule {
        init { require(hours in 1..24) { "hours must be 1..24" } }
    }

    @Serializable @SerialName("cyclic")
    /** [daysOn] days taking it, then [daysOff] days resting, counted from the treatment start. */
    data class Cyclic(val daysOn: Int, val daysOff: Int, val times: List<LocalTime>) : Schedule {
        init { require(daysOn > 0 && daysOff >= 0) { "invalid cycle" } }
    }

    @Serializable @SerialName("as_needed")
    data object AsNeeded : Schedule
}

@Serializable
enum class DoseStatus { TAKEN, SKIPPED }

@Serializable
data class DoseNote(
    val chips: Set<String> = emptySet(),
    val text: String? = null,
    /** 1 (muy mal) .. 5 (muy bien). */
    val mood: Int? = null,
)

@Serializable
data class Dose(
    val id: String,
    val medicineId: String,
    val at: LocalDateTime,
    val status: DoseStatus,
    val amount: String? = null,
    val note: DoseNote? = null,
)
