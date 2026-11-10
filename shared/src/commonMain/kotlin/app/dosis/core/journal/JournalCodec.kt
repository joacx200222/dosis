package app.dosis.core.journal

import app.dosis.core.model.Dose
import app.dosis.core.model.Medicine
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * Saves the journal as JSON (a local file on Android). The same text is what gets
 * encrypted and synced to Supabase later.
 */
object JournalCodec {
    private const val VERSION = 1

    @Serializable
    private data class Saved(val version: Int, val medicines: List<Medicine>, val doses: List<Dose>)

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = false
    }

    fun encode(state: JournalState): String =
        json.encodeToString(Saved.serializer(), Saved(VERSION, state.medicines.values.toList(), state.doses))

    fun decode(text: String): JournalState {
        val saved = json.decodeFromString(Saved.serializer(), text)
        return JournalState(saved.medicines.associateBy { it.id }, saved.doses)
    }

    /** Random ids, so ids from the phone, watch and web never collide once they sync. */
    @OptIn(ExperimentalUuidApi::class)
    fun newId(): String = Uuid.random().toString()
}
