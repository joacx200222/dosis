package app.dosis.android.reminders

import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Duration
import kotlin.time.ExperimentalTime

@OptIn(ExperimentalTime::class)
internal fun LocalDateTime.epochMillis(): Long = toInstant(TimeZone.currentSystemDefault()).toEpochMilliseconds()

@OptIn(ExperimentalTime::class)
internal fun LocalDateTime.shift(d: Duration): LocalDateTime =
    (toInstant(TimeZone.currentSystemDefault()) + d).toLocalDateTime(TimeZone.currentSystemDefault())
