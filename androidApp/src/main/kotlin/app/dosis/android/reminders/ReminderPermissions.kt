package app.dosis.android.reminders

import android.Manifest
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.ContextCompat

/** What the phone must allow for reminders to work, and where the user turns each one on. */
object ReminderPermissions {

    enum class Item(val title: String, val why: String, val onlyForStrong: Boolean) {
        NOTIFICATIONS("Notificaciones", "Para avisarte a la hora de cada toma.", false),
        EXACT_ALARMS("Alarmas a la hora exacta", "Sin esto, Android puede atrasar el aviso varios minutos.", false),
        FULL_SCREEN("Alerta a pantalla completa", "Para que el recordatorio fuerte aparezca aunque el teléfono esté bloqueado.", true),
        DO_NOT_DISTURB("Sonar en No molestar", "Para que el recordatorio fuerte suene aunque tengas No molestar activado.", true),
    }

    fun missing(context: Context, anyStrong: Boolean): List<Item> =
        Item.entries.filter { (anyStrong || !it.onlyForStrong) && !granted(context, it) }

    fun granted(context: Context, item: Item): Boolean = when (item) {
        Item.NOTIFICATIONS -> Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        Item.EXACT_ALARMS -> ReminderScheduler.canScheduleExact(context)
        Item.FULL_SCREEN -> Build.VERSION.SDK_INT < 34 ||
            context.getSystemService(NotificationManager::class.java).canUseFullScreenIntent()
        Item.DO_NOT_DISTURB -> context.getSystemService(NotificationManager::class.java).isNotificationPolicyAccessGranted
    }

    /** Settings screen for items that cannot be asked with a runtime dialog. */
    fun settingsIntent(context: Context, item: Item): Intent {
        val pkg = Uri.parse("package:${context.packageName}")
        return when (item) {
            Item.NOTIFICATIONS -> Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
            Item.EXACT_ALARMS -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, pkg)
            } else {
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, pkg)
            }
            Item.FULL_SCREEN -> if (Build.VERSION.SDK_INT >= 34) {
                Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, pkg)
            } else {
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, pkg)
            }
            Item.DO_NOT_DISTURB -> Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)
        }.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
}
