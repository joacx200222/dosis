package app.dosis.android.reminders

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import app.dosis.android.MainActivity
import app.dosis.core.journal.JournalState
import app.dosis.core.model.ReminderLevel
import app.dosis.core.schedule.ReminderKind
import app.dosis.core.schedule.ReminderPlanner
import app.dosis.core.time.nowLocal
import kotlinx.datetime.LocalDateTime
import kotlin.time.Duration.Companion.minutes

/**
 * Keeps exactly ONE alarm set: the next reminder from [ReminderPlanner]. When it fires,
 * [ReminderReceiver] shows what is due and calls [reschedule] again. Snoozes get their own alarm.
 */
object ReminderScheduler {
    private const val REQUEST_NEXT = 1

    fun canScheduleExact(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            context.getSystemService(AlarmManager::class.java).canScheduleExactAlarms()

    fun reschedule(context: Context, state: JournalState) {
        val am = context.getSystemService(AlarmManager::class.java)
        val fire = PendingIntent.getBroadcast(
            context, REQUEST_NEXT,
            Intent(context, ReminderReceiver::class.java).setAction(ReminderReceiver.ACTION_FIRE),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val next = ReminderPlanner.nextBatch(state, nowLocal())
        if (next == null) {
            am.cancel(fire)
            return
        }
        val (at, batch) = next
        set(context, am, at.epochMillis(), fire, strong = batch.any { it.level == ReminderLevel.STRONG })
    }

    fun snooze(context: Context, medicineId: String, scheduledAt: LocalDateTime, minutes: Int = 10) {
        val am = context.getSystemService(AlarmManager::class.java)
        val intent = Intent(context, ReminderReceiver::class.java).setAction(ReminderReceiver.ACTION_SNOOZE_FIRE)
            .putExtra(ReminderNotifications.EXTRA_MEDICINE, medicineId)
            .putExtra(ReminderNotifications.EXTRA_SCHEDULED, scheduledAt.toString())
            .putExtra(ReminderNotifications.EXTRA_KIND, ReminderKind.FOLLOW_UP.name)
        val pi = PendingIntent.getBroadcast(
            context, ReminderNotifications.id(medicineId, scheduledAt), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        set(context, am, nowLocal().shift(minutes.minutes).epochMillis(), pi, strong = false)
    }

    private fun set(context: Context, am: AlarmManager, millis: Long, pi: PendingIntent, strong: Boolean) {
        when {
            // Alarm-clock alarms are the most reliable (exempt from Doze) and show the alarm icon.
            strong && canScheduleExact(context) -> {
                val show = PendingIntent.getActivity(
                    context, 0, Intent(context, MainActivity::class.java),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                )
                am.setAlarmClock(AlarmManager.AlarmClockInfo(millis, show), pi)
            }
            canScheduleExact(context) -> am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, millis, pi)
            else -> am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, millis, pi)
        }
    }
}
