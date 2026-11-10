package app.dosis.core.journal

import app.dosis.core.data.SearchHit
import app.dosis.core.data.InteractionData
import app.dosis.core.interactions.InteractionEngine
import app.dosis.core.interactions.InteractionReport
import app.dosis.core.model.Dose
import app.dosis.core.model.DoseNote
import app.dosis.core.model.DoseStatus
import app.dosis.core.model.Medicine
import app.dosis.core.schedule.ScheduleCalculator
import app.dosis.core.schedule.ScheduledDose
import app.dosis.core.search.MedicineSuggester
import app.dosis.core.text.normalizeName
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime

data class JournalState(
    val medicines: Map<String, Medicine> = emptyMap(),
    val doses: List<Dose> = emptyList(),
) {
    val cabinet: List<Medicine> get() = medicines.values.filter { it.inCabinet }.sortedBy { it.name }
    val oneOffs: List<Medicine> get() = medicines.values.filterNot { it.inCabinet }.sortedBy { it.name }

    fun dosesOn(date: LocalDate): List<Dose> = doses.filter { it.at.date == date }.sortedBy { it.at }

    /** Most recently logged medicines first, without repeats. */
    fun recent(limit: Int = 3): List<Medicine> =
        doses.sortedByDescending { it.at }
            .mapNotNull { medicines[it.medicineId] }
            .distinctBy { it.id }
            .take(limit)
}

/**
 * In-memory journal shared by every platform UI. Persistence (Room / IndexedDB),
 * Wear Data Layer and Supabase sync plug in behind this later.
 */
class Journal(
    private val engine: InteractionEngine,
    initial: JournalState = JournalState(),
    private val newId: () -> String,
) {
    private val _state = MutableStateFlow(initial)
    val state: StateFlow<JournalState> = _state.asStateFlow()

    /** Bundled interaction data; null while loading. Re-run [preview] when it changes. */
    val interactionData: StateFlow<InteractionData?> get() = engine.data

    /** Interaction preview shown BEFORE confirming a dose or adding to the cabinet. */
    fun preview(medicine: Medicine): InteractionReport = engine.check(medicine, _state.value.cabinet)

    /**
     * Finds a known medicine by display name, or creates one on the fly (no pre-registration needed).
     * Ingredients come from the bundled lexicon, so "Apronax" or "naproxeno 550" get naproxen's data.
     */
    fun resolve(typedName: String, strength: String? = null): Medicine {
        val key = normalizeName(listOfNotNull(typedName, strength).joinToString(" "))
        return _state.value.medicines.values.firstOrNull { normalizeName(it.displayName) == key }
            ?: Medicine(
                id = newId(),
                name = typedName.trim(),
                strength = strength?.trim(),
                ingredients = engine.ingredientsFor(typedName),
                inCabinet = false,
            )
    }

    /**
     * Suggestions for "¿Qué tomaste?": recents, then saved medicines, then common medicines from the
     * bundled data (ids start with [LEXICON_ID]; [logDose] turns them into real medicines).
     */
    fun suggest(query: String, limit: Int = 6): List<Medicine> {
        val s = _state.value
        val saved = s.medicines.values.toList()
        val q = normalizeName(query)
        if (q.length < 2) return MedicineSuggester.suggest(query, s.recent(5), saved, limit)

        fun starts(n: String) = n.startsWith(q) || n.contains(" $q")

        // 1. Your own medicines whose name starts with the text; weaker "contains" matches go last.
        val (savedStrong, savedWeak) = MedicineSuggester.suggest(query, s.recent(5), saved, limit)
            .partition { starts(normalizeName(it.name)) || it.ingredients.any { i -> i.startsWith(q) } }

        // 2. The bundled list: exact name first, then common medicines (and their hand-written brands),
        //    other official names, registry brands, and generic combinations last; shorter names first.
        val savedNames = saved.map { normalizeName(it.name) }.toSet()
        val lx = engine.lexicon
        val fromData = lx.searchIndex.asSequence()
            .filter { starts(it.norm) && it.norm !in savedNames }
            .sortedWith(
                compareBy<SearchHit>(
                    { it.norm != q },
                    { it.kind.ordinal },
                    { !it.norm.startsWith(q) },
                    { it.name.length },
                    { it.name },
                ),
            )
            .take(limit * 3)
            .map { h ->
                val single = h.keys.singleOrNull()
                val id = if (single != null && lx.entry(single)?.displayName == h.name) single else "name:" + h.norm
                Medicine(id = LEXICON_ID + id, name = h.name, ingredients = h.keys, inCabinet = false)
            }
            .toList()

        return (savedStrong + fromData + savedWeak)
            .distinctBy { normalizeName(it.displayName) }
            .take(limit)
    }

    /** True when [medicine]'s name is a brand or combination, not the ingredient's own name. */
    fun isBrandName(medicine: Medicine): Boolean {
        val single = medicine.ingredients.singleOrNull() ?: return medicine.ingredients.size > 1
        val e = engine.lexicon.entry(single) ?: return false
        return normalizeName(medicine.name) != normalizeName(e.displayName)
    }

    /** Spanish names of a medicine's ingredients, e.g. for "Acriptega": Tenofovir, Lamivudina, Dolutegravir. */
    fun ingredientNames(medicine: Medicine): List<String> =
        medicine.ingredients.map { engine.lexicon.entry(it)?.displayName ?: it }

    fun logDose(
        medicine: Medicine,
        at: LocalDateTime,
        addToCabinet: Boolean,
        status: DoseStatus = DoseStatus.TAKEN,
        amount: String? = null,
    ): Dose {
        val med = if (medicine.id.startsWith(LEXICON_ID)) {
            _state.value.medicines.values.firstOrNull { normalizeName(it.name) == normalizeName(medicine.name) }
                ?: medicine.copy(id = newId())
        } else medicine
        val dose = Dose(newId(), med.id, at, status, amount)
        _state.update { s ->
            val existing = s.medicines[med.id] ?: med
            val updated = existing.copy(
                inCabinet = existing.inCabinet || addToCabinet,
                stock = existing.stock?.let {
                    if (status == DoseStatus.TAKEN) it.copy(remaining = (it.remaining - 1).coerceAtLeast(0)) else it
                },
            )
            s.copy(medicines = s.medicines + (updated.id to updated), doses = s.doses + dose)
        }
        return dose
    }

    fun addNote(doseId: String, note: DoseNote) = _state.update { s ->
        s.copy(doses = s.doses.map { if (it.id == doseId) it.copy(note = note) else it })
    }

    fun upsertMedicine(medicine: Medicine) = _state.update { s ->
        s.copy(medicines = s.medicines + (medicine.id to medicine))
    }

    fun setInCabinet(medicineId: String, inCabinet: Boolean) = _state.update { s ->
        val m = s.medicines[medicineId] ?: return@update s
        s.copy(medicines = s.medicines + (m.id to m.copy(inCabinet = inCabinet)))
    }

    fun nextDose(now: LocalDateTime): ScheduledDose? =
        ScheduleCalculator.nextDose(_state.value.cabinet, now)

    companion object {
        const val LEXICON_ID = "lex:"

    }
}
