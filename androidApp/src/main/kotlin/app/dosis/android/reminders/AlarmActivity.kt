package app.dosis.android.reminders

import android.media.AudioAttributes
import android.media.Ringtone
import android.media.RingtoneManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import app.dosis.android.AppGraph
import app.dosis.android.ui.components.Pill
import app.dosis.android.ui.theme.DosisTheme
import app.dosis.android.ui.theme.Ink
import app.dosis.core.model.DoseStatus
import app.dosis.core.model.Medicine
import app.dosis.core.schedule.ReminderKind
import app.dosis.core.schedule.ReminderPlanner
import app.dosis.core.time.hhmm
import app.dosis.core.time.nowLocal
import kotlinx.datetime.LocalDateTime

/**
 * Strong reminder: full screen over the lock screen, alarm sound on the ALARM stream
 * (so it rings in silent mode and in the default "No molestar", which lets alarms through).
 */
class AlarmActivity : ComponentActivity() {
    private var ringtone: Ringtone? = null
    private var vibrator: Vibrator? = null
    private val stopper = Handler(Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON)
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        val medId = intent.getStringExtra(ReminderNotifications.EXTRA_MEDICINE)
        val at = intent.getStringExtra(ReminderNotifications.EXTRA_SCHEDULED)
            ?.let { runCatching { LocalDateTime.parse(it) }.getOrNull() }
        val med = medId?.let { AppGraph.journal.state.value.medicines[it] }
        if (med == null || at == null) {
            finish()
            return
        }
        val kind = runCatching { ReminderKind.valueOf(intent.getStringExtra(ReminderNotifications.EXTRA_KIND) ?: "") }
            .getOrDefault(ReminderKind.DUE)

        startAlarm()
        setContent {
            DosisTheme {
                // Closes by itself if the dose gets logged elsewhere (notification button, watch).
                val state by AppGraph.journal.state.collectAsState()
                LaunchedEffect(state) { if (ReminderPlanner.isHandled(state, med.id, at)) finish() }
                AlarmScreen(
                    medicine = med,
                    scheduledAt = at,
                    kind = kind,
                    warning = ReminderNotifications.warningLine(med),
                    onTaken = { log(med, at, DoseStatus.TAKEN) },
                    onSkip = { log(med, at, DoseStatus.SKIPPED) },
                    onSnooze = {
                        ReminderNotifications.cancel(this, med.id, at)
                        ReminderScheduler.snooze(this, med.id, at)
                        finish()
                    },
                    onSilence = { stopAlarm() },
                )
            }
        }
    }

    private fun log(med: Medicine, at: LocalDateTime, status: DoseStatus) {
        AppGraph.journal.logDose(med, nowLocal(), addToCabinet = true, status = status)
        ReminderNotifications.cancel(this, med.id, at)
        AppGraph.flush()
        finish()
    }

    private fun startAlarm() {
        val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
        ringtone = RingtoneManager.getRingtone(this, uri)?.apply {
            audioAttributes = AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) isLooping = true
            play()
        }
        vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            getSystemService(VibratorManager::class.java).defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(Vibrator::class.java)
        }
        vibrator?.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 700, 500), 0))
        // Never ring forever: stop after 3 minutes; the notification stays.
        stopper.postDelayed({ stopAlarm() }, 3 * 60 * 1000L)
    }

    private fun stopAlarm() {
        ringtone?.stop()
        vibrator?.cancel()
        stopper.removeCallbacksAndMessages(null)
    }

    override fun onDestroy() {
        stopAlarm()
        super.onDestroy()
    }
}

@Composable
private fun AlarmScreen(
    medicine: Medicine,
    scheduledAt: LocalDateTime,
    kind: ReminderKind,
    warning: String?,
    onTaken: () -> Unit,
    onSkip: () -> Unit,
    onSnooze: () -> Unit,
    onSilence: () -> Unit,
) {
    Column(
        Modifier.fillMaxSize().background(Ink.Paper).safeDrawingPadding().padding(28.dp),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Column {
            Text("dosis", style = MaterialTheme.typography.headlineMedium, color = Ink.Blue)
            Spacer(Modifier.height(48.dp))
            Pill(medicine.appearance, height = 64.dp)
            Spacer(Modifier.height(24.dp))
            Text(
                if (kind == ReminderKind.DUE) "Hora de tu" else "¿Ya la tomaste?",
                style = MaterialTheme.typography.headlineMedium,
                color = Ink.Muted,
            )
            Text(medicine.displayName, style = MaterialTheme.typography.displayLarge)
            Text("Programada a las ${scheduledAt.hhmm()}", style = MaterialTheme.typography.titleMedium, color = Ink.Muted)
            warning?.let {
                Text(
                    "!! $it",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White,
                    modifier = Modifier.padding(top = 24.dp).fillMaxWidth()
                        .background(Ink.Pink, MaterialTheme.shapes.large).padding(16.dp),
                )
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Button(
                onClick = onTaken,
                colors = ButtonDefaults.buttonColors(containerColor = Ink.Pink),
                modifier = Modifier.fillMaxWidth().height(64.dp),
            ) { Text("Tomada", style = MaterialTheme.typography.titleLarge) }
            Spacer(Modifier.height(12.dp))
            OutlinedButton(onClick = onSnooze, modifier = Modifier.fillMaxWidth().height(56.dp)) { Text("Posponer 10 min") }
            Spacer(Modifier.height(8.dp))
            TextButton(onClick = onSkip) { Text("Omitir esta toma") }
            TextButton(onClick = onSilence) { Text("Silenciar", color = Ink.Muted) }
        }
    }
}
