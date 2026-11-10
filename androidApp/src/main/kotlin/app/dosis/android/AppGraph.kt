package app.dosis.android

import android.content.Context
import android.util.AtomicFile
import app.dosis.android.reminders.ReminderNotifications
import app.dosis.android.reminders.ReminderScheduler
import app.dosis.core.data.InteractionDataParser
import app.dosis.core.data.InteractionDataStore
import app.dosis.core.interactions.InteractionEngine
import app.dosis.core.journal.Journal
import app.dosis.core.journal.JournalCodec
import app.dosis.core.journal.JournalState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.io.File
import kotlin.concurrent.thread

/**
 * Process-wide state, created in [DosisApplication] so reminder broadcasts get the same
 * journal as the UI. The journal is saved to files/journal.json on every change.
 */
object AppGraph {
    private lateinit var app: Context
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** Bundled DDInter + TripSit + dosis tables (assets/interactions.tsv), parsed off the main thread. */
    val interactionData = InteractionDataStore()

    val journal: Journal by lazy {
        Journal(InteractionEngine(interactionData), load()) { JournalCodec.newId() }
    }

    fun init(context: Context) {
        app = context.applicationContext
        ReminderNotifications.createChannels(app)
        thread(name = "dosis-interactions", isDaemon = true) {
            val text = app.assets.open("interactions.tsv").bufferedReader().use { it.readText() }
            interactionData.set(InteractionDataParser.parse(text))
        }
        // Save and re-plan the next alarm whenever the journal changes (and once at start).
        scope.launch {
            journal.state.collect { state ->
                save(state)
                ReminderScheduler.reschedule(app, state)
            }
        }
    }

    /** Synchronous save + re-plan, for broadcast receivers that may be killed right after returning. */
    fun flush() {
        val state = journal.state.value
        save(state)
        ReminderScheduler.reschedule(app, state)
    }

    private val file by lazy { AtomicFile(File(app.filesDir, "journal.json")) }

    private fun load(): JournalState =
        runCatching { JournalCodec.decode(file.readFully().decodeToString()) }.getOrElse { JournalState() }

    @Synchronized
    private fun save(state: JournalState) {
        val out = file.startWrite()
        try {
            out.write(JournalCodec.encode(state).encodeToByteArray())
            file.finishWrite(out)
        } catch (e: Exception) {
            file.failWrite(out)
        }
    }
}
