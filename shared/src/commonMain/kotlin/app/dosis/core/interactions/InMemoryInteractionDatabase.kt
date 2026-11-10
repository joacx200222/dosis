package app.dosis.core.interactions

import app.dosis.core.text.normalizeName

/** Simple map-backed database; the real data files (DDInter, TripSit, curated table) load into this. */
class InMemoryInteractionDatabase(
    drugPairs: Map<Pair<String, String>, KnownInteraction>,
    substances: Map<Pair<String, Substance>, KnownInteraction>,
) : InteractionDatabase {

    private val pairs = drugPairs.mapKeys { (k, _) -> key(k.first, k.second) }
    private val subs = substances.mapKeys { (k, _) -> normalizeName(k.first) to k.second }

    override fun drugDrug(a: String, b: String): KnownInteraction? = pairs[key(a, b)]

    override fun drugSubstance(ingredient: String, substance: Substance): KnownInteraction? =
        subs[normalizeName(ingredient) to substance]

    private fun key(a: String, b: String): Pair<String, String> {
        val x = normalizeName(a)
        val y = normalizeName(b)
        return if (x <= y) x to y else y to x
    }
}
