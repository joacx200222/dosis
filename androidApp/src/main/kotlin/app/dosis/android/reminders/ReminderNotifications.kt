package app.dosis.android.reminders

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import app.dosis.android.AppGraph
import app.dosis.android.MainActivity
import app.dosis.android.R
import app.dosis.core.interactions.Severity
import app.dosis.core.journal.JournalState
import app.dosis.core.model.Medicine
import app.dosis.core.model.ReminderLevel
import app.dosis.core.schedule.ReminderKind
import app.dosis.core.time.hhmm
import kotlinx.datetime.LocalDateTime

/** One notification per planned dose; the follow-up replaces the first one (same id). */
object ReminderNotifications {
    const val CHANNEL_SOFT = "recordatorios"
    const val CHANNEL_STRONG = "recordatorios_fuertes"

    /** Strong reminders when the full-screen alarm can show: AlarmActivity plays the (looping) sound itself. */
    const val CHANNEL_STRONG_SCREEN = "recordatorios_fuertes_pantalla"

    const val EXTRA_MEDICINE = "medicineId"
    const val EXTRA_SCHEDULED = "scheduledAt"
    const val EXTRA_KIND = "kind"

    fun createChannels(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java)
        val soft = NotificationChannel(CHANNEL_SOFT, "Recordatorios", NotificationManager.IMPORTANCE_HIGH).apply {
            description = "Aviso normal a la hora de cada toma."
            lockscreenVisibility = NotificationCompat.VISIBILITY_PRIVATE
        }
        val strong = NotificationChannel(CHANNEL_STRONG, "Recordatorios fuertes", NotificationManager.IMPORTANCE_HIGH).apply {
            description = "Alarma a pantalla completa que suena aunque el teléfono esté en silencio."
            setSound(
                RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM),
                AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build(),
            )
            enableVibration(true)
            vibrationPattern = longArrayOf(0, 600, 400, 600, 400, 600)
            // Honored only once the user grants "No molestar" access (see ReminderPermissions).
            setBypassDnd(true)
            lockscreenVisibility = NotificationCompat.VISIBILITY_PUBLIC
        }
        val screen = NotificationChannel(CHANNEL_STRONG_SCREEN, "Recordatorios fuertes (alarma en pantalla)", NotificationManager.IMPORTANCE_HIGH).apply {
            description = "La alarma a pantalla completa suena por su cuenta; este aviso es silencioso para no sonar doble."
            setSound(null, null)
            enableVibration(false)
            setBypassDnd(true)
            lockscreenVisibility = NotificationCompat.VISIBILITY_PUBLIC
        }
        nm.createNotificationChannels(listOf(soft, strong, screen))
    }

    fun id(medicineId: String, scheduledAt: LocalDateTime): Int = "$medicineId@$scheduledAt".hashCode()

    fun show(context: Context, state: JournalState, medicineId: String, scheduledAt: LocalDateTime, kind: ReminderKind) {
        val med = state.medicines[medicineId] ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return

        val strong = med.treatment?.reminderLevel == ReminderLevel.STRONG
        val notifId = id(medicineId, scheduledAt)
        val title = when (kind) {
            ReminderKind.DUE -> "Hora de tu ${med.displayName}"
            ReminderKind.FOLLOW_UP -> "¿Ya tomaste ${med.name}?"
        }
        val text = when (kind) {
            ReminderKind.DUE -> "Programada a las ${scheduledAt.hhmm()}."
            ReminderKind.FOLLOW_UP -> "Era a las ${scheduledAt.hhmm()}. Regístrala para no olvidarte."
        }
        val details = listOfNotNull(text, warningLine(med), stockLine(med)).joinToString("\n")

        val open = PendingIntent.getActivity(
            context, notifId, Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val fullScreen = strong && ReminderPermissions.granted(context, ReminderPermissions.Item.FULL_SCREEN)
        val channel = when {
            fullScreen -> CHANNEL_STRONG_SCREEN
            strong -> CHANNEL_STRONG
            else -> CHANNEL_SOFT
        }
        val builder = NotificationCompat.Builder(context, channel)
            .setSmallIcon(R.drawable.ic_dosis_pill)
            .setColor(0xFFFF48B0.toInt())
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(details))
            .setWhen(scheduledAt.epochMillis())
            .setShowWhen(true)
            .setContentIntent(open)
            .setAutoCancel(!strong)
            .setOngoing(strong)
            .setCategory(if (strong) NotificationCompat.CATEGORY_ALARM else NotificationCompat.CATEGORY_REMINDER)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setVisibility(if (strong) NotificationCompat.VISIBILITY_PUBLIC else NotificationCompat.VISIBILITY_PRIVATE)
            .setPublicVersion(
                NotificationCompat.Builder(context, CHANNEL_SOFT)
                    .setSmallIcon(R.drawable.ic_dosis_pill)
                    .setContentTitle("dosis")
                    .setContentText("Tienes una toma pendiente.")
                    .build(),
            )
            .addAction(0, "Tomada", action(context, ReminderReceiver.ACTION_TAKEN, medicineId, scheduledAt, kind))
            .addAction(0, "Posponer 10 min", action(context, ReminderReceiver.ACTION_SNOOZE, medicineId, scheduledAt, kind))
            .addAction(0, "Omitir", action(context, ReminderReceiver.ACTION_SKIP, medicineId, scheduledAt, kind))

        if (strong) {
            builder.setFullScreenIntent(alarmScreen(context, medicineId, scheduledAt, kind), true)
        }
        NotificationManagerCompat.from(context).notify(notifId, builder.build())
    }

    fun cancel(context: Context, medicineId: String, scheduledAt: LocalDateTime) =
        NotificationManagerCompat.from(context).cancel(id(medicineId, scheduledAt))

    fun alarmScreen(context: Context, medicineId: String, scheduledAt: LocalDateTime, kind: ReminderKind): PendingIntent {
        val alarm = Intent(context, AlarmActivity::class.java)
            .putExtra(EXTRA_MEDICINE, medicineId)
            .putExtra(EXTRA_SCHEDULED, scheduledAt.toString())
            .putExtra(EXTRA_KIND, kind.name)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_USER_ACTION)
        return PendingIntent.getActivity(
            context, id(medicineId, scheduledAt), alarm,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun action(context: Context, act: String, medicineId: String, scheduledAt: LocalDateTime, kind: ReminderKind): PendingIntent {
        val intent = Intent(context, ReminderReceiver::class.java).setAction(act)
            .putExtra(EXTRA_MEDICINE, medicineId)
            .putExtra(EXTRA_SCHEDULED, scheduledAt.toString())
            .putExtra(EXTRA_KIND, kind.name)
        val code = id(medicineId, scheduledAt) * 31 + act.hashCode()
        return PendingIntent.getBroadcast(context, code, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    /** Short line with the worst warnings, so "Tomada" from the notification never hides them. */
    fun warningLine(med: Medicine): String? {
        val report = AppGraph.journal.preview(med)
        if (!report.dataReady) return null
        val cabinet = report.withCabinet.filter { it.interaction.severity == Severity.MAJOR }.map { it.other.name }
        val substances = report.alwaysConsider.filter { it.severity == Severity.MAJOR }.map { it.substance.label }
        val parts = listOfNotNull(
            cabinet.takeIf { it.isNotEmpty() }?.let { "con ${it.joinToString(", ")} de tu botiquín" },
            substances.takeIf { it.isNotEmpty() }?.let { "con " + it.take(6).joinToString(", ") + if (it.size > 6) "…" else "" },
        )
        return if (parts.isEmpty()) null else "Grave " + parts.joinToString(" y ") + "."
    }

    private fun stockLine(med: Medicine): String? =
        med.stock?.takeIf { it.isLow }?.let { "Te quedan ${it.remaining}; toca reponer." }
}
