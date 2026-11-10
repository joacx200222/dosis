package app.dosis.android.reminders

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import app.dosis.android.AppGraph
import app.dosis.core.model.DoseStatus
import app.dosis.core.schedule.ReminderKind
import app.dosis.core.schedule.ReminderPlanner
import app.dosis.core.time.nowLocal
import kotlinx.datetime.LocalDateTime
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes

/** Alarm fired, notification buttons, and system events that require re-planning the alarm. */
class ReminderReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ACTION_FIRE -> showDue(context)
            ACTION_TAKEN -> log(context, intent, DoseStatus.TAKEN)
            ACTION_SKIP -> log(context, intent, DoseStatus.SKIPPED)
            ACTION_SNOOZE -> {
                val (med, at) = target(intent) ?: return
                ReminderNotifications.cancel(context, med, at)
                ReminderScheduler.snooze(context, med, at)
            }
            ACTION_SNOOZE_FIRE -> {
                val (med, at) = target(intent) ?: return
                val state = AppGraph.journal.state.value
                if (!ReminderPlanner.isHandled(state, med, at)) {
                    ReminderNotifications.show(context, state, med, at, ReminderKind.FOLLOW_UP)
                }
            }
            // Boot, clock or time-zone change, app update, exact-alarm permission granted.
            else -> AppGraph.flush()
        }
    }

    /** Shows every reminder whose time passed since the last check (robust to late alarms). */
    private fun showDue(context: Context) {
        val prefs = context.getSharedPreferences("reminders", Context.MODE_PRIVATE)
        val now = nowLocal()
        val floor = now.shift(-12.hours)
        val last = prefs.getString(KEY_LAST_CHECK, null)
            ?.let { runCatching { LocalDateTime.parse(it) }.getOrNull() }
            ?.takeIf { it in floor..now }
            ?: now.shift(-15.minutes)
        val state = AppGraph.journal.state.value
        ReminderPlanner.events(state, last)
            .filter { it.fireAt <= now }
            // One notification per dose: the follow-up wins over the first reminder.
            .groupBy { it.medicineId to it.scheduledAt }
            .values.map { group -> group.maxBy { it.fireAt } }
            .forEach { ReminderNotifications.show(context, state, it.medicineId, it.scheduledAt, it.kind) }
        prefs.edit().putString(KEY_LAST_CHECK, now.toString()).apply()
        AppGraph.flush()
    }

    private fun log(context: Context, intent: Intent, status: DoseStatus) {
        val (medId, at) = target(intent) ?: return
        val med = AppGraph.journal.state.value.medicines[medId] ?: return
        AppGraph.journal.logDose(med, nowLocal(), addToCabinet = true, status = status)
        ReminderNotifications.cancel(context, medId, at)
        AppGraph.flush()
    }

    private fun target(intent: Intent): Pair<String, LocalDateTime>? {
        val med = intent.getStringExtra(ReminderNotifications.EXTRA_MEDICINE) ?: return null
        val at = intent.getStringExtra(ReminderNotifications.EXTRA_SCHEDULED)
            ?.let { runCatching { LocalDateTime.parse(it) }.getOrNull() } ?: return null
        return med to at
    }

    companion object {
        const val ACTION_FIRE = "app.dosis.reminder.FIRE"
        const val ACTION_TAKEN = "app.dosis.reminder.TAKEN"
        const val ACTION_SKIP = "app.dosis.reminder.SKIP"
        const val ACTION_SNOOZE = "app.dosis.reminder.SNOOZE"
        const val ACTION_SNOOZE_FIRE = "app.dosis.reminder.SNOOZE_FIRE"
        private const val KEY_LAST_CHECK = "lastCheck"
    }
}
