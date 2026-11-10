package app.dosis.core.interactions

import app.dosis.core.data.DrugLexicon
import app.dosis.core.data.InteractionData
import app.dosis.core.data.InteractionDataStore
import app.dosis.core.model.Medicine
import app.dosis.core.text.normalizeName
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Rules agreed for dosis:
 *
 * 1. "Con tu botiquín": [medicine] is checked against EVERY medicine saved in the
 *    cabinet, regardless of when it was last taken. Real severity is kept.
 * 2. "Ten en cuenta siempre": every [Substance] (alcohol, tobacco, every recreational
 *    drug, foods, supplements) is always listed, even if the user never consumes it,
 *    with a floor of [Severity.MODERATE].
 * 3. With no data for a substance, it is still listed as MODERATE (precaution) and
 *    flagged with hasData = false — an empty list must never look like "safe".
 */
class InteractionEngine(
    /** Null until the bundled file has loaded; UIs collect it to refresh warnings when it arrives. */
    val data: StateFlow<InteractionData?>,
) {
    constructor(db: InteractionDatabase, lexicon: DrugLexicon = DrugLexicon.EMPTY) :
        this(MutableStateFlow(InteractionData(db, lexicon)))

    constructor(store: InteractionDataStore) : this(store.data)

    private fun source(): InteractionData? = data.value

    /** Canonical ingredient keys for a typed name (empty until the data has loaded or if unknown). */
    fun ingredientsFor(name: String): Set<String> = source()?.lexicon?.ingredientsFor(name).orEmpty()

    val lexicon: DrugLexicon get() = source()?.lexicon ?: DrugLexicon.EMPTY

    fun check(medicine: Medicine, cabinet: Collection<Medicine>): InteractionReport {
        val data = source()
        val db = data?.database
        val mine = ingredientsOf(medicine, data)
        return InteractionReport(
            medicine = medicine,
            withCabinet = if (db == null) emptyList() else cabinetWarnings(medicine, mine, cabinet, db, data),
            alwaysConsider = Substance.entries.map { substanceWarning(mine, it, db) },
            dataReady = data != null,
        )
    }

    /** Saved ingredients, or resolved from the name for medicines created before data loaded. */
    private fun ingredientsOf(m: Medicine, data: InteractionData?): Set<String> =
        m.ingredients.ifEmpty {
            data?.lexicon?.ingredientsFor(m.name)?.ifEmpty { null } ?: setOf(normalizeName(m.name))
        }

    private fun cabinetWarnings(
        medicine: Medicine,
        mine: Set<String>,
        cabinet: Collection<Medicine>,
        db: InteractionDatabase,
        data: InteractionData,
    ): List<CabinetWarning> =
        cabinet
            .filter { it.inCabinet && it.id != medicine.id }
            .mapNotNull { other ->
                // Keep the worst interaction per cabinet medicine.
                mine.flatMap { a ->
                    ingredientsOf(other, data).mapNotNull { b ->
                        if (a == b) null else db.drugDrug(a, b)?.let { CabinetWarning(other, a, b, it) }
                    }
                }.maxByOrNull { it.interaction.severity }
            }
            .sortedByDescending { it.interaction.severity }

    private fun substanceWarning(ingredients: Set<String>, substance: Substance, db: InteractionDatabase?): AlwaysWarning {
        val worst = db?.let { ingredients.mapNotNull { i -> it.drugSubstance(i, substance) }.maxByOrNull { it.severity } }
        return if (worst == null) {
            AlwaysWarning(substance, Severity.MODERATE, hasData = false, description = null, source = null)
        } else {
            AlwaysWarning(
                substance = substance,
                severity = maxOf(worst.severity, Severity.MODERATE),
                hasData = true,
                description = worst.description,
                source = worst.source,
            )
        }
    }
}
