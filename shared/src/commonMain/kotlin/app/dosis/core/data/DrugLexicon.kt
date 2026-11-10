package app.dosis.core.data

import app.dosis.core.text.normalizeName

/** One known ingredient. [key] is the canonical ingredient id used by the interaction data. */
data class DrugEntry(
    val key: String,
    val english: String,
    val spanish: String?,
    /** Hand-written and official names (aliases_es.tsv, CIMA): "Apronax", "TLD", "Metamizol sódico". */
    val aliases: List<String>,
    /** Hand-curated common medicine (tools/aliases_es.tsv); ranks first in search. */
    val popular: Boolean = false,
    /** Brands from the product registries (INVIMA, CIMA, RxNorm). Listed on several entries = combination. */
    val products: List<String> = emptyList(),
) {
    val displayName: String get() = spanish?.takeIf { it.isNotBlank() } ?: english
}

/** One row of the search box: a medicine name, a brand or a combination, with its ingredients. */
data class SearchHit(
    /** Normalized [name], for prefix matching. */
    val norm: String,
    val name: String,
    val keys: Set<String>,
    val kind: Kind,
) {
    enum class Kind {
        /** Ingredient name of a hand-curated common medicine ("Paracetamol"), or its hand-written brand ("Apronax"). */
        COMMON,

        /** Any other ingredient name from the official lists ("Ceftazidima"). */
        INGREDIENT,

        /** A brand from the product registries ("Acetadol", "Losacor"). */
        BRAND,

        /** A generic combination name ("Valsartan, amlodipino e hidroclorotiazida"). */
        COMBINATION,
    }
}

/**
 * Maps whatever the user types (Spanish name, English name, brand, "Paracetamol 500 mg",
 * "Amoxicilina + ácido clavulánico") to canonical ingredient keys.
 */
class DrugLexicon(val entries: List<DrugEntry>, guesses: Map<String, List<String>> = emptyMap()) {

    private val exact: Map<String, Set<String>>
    private val guessed: Map<String, Set<String>>

    init {
        val e = HashMap<String, MutableSet<String>>()
        for (d in entries) {
            (listOf(d.english, d.spanish.orEmpty()) + d.aliases + d.products)
                .map { normalizeName(it) }.filter { it.isNotEmpty() }
                .forEach { e.getOrPut(it) { HashSet() }.add(d.key) }
        }
        exact = e
        val g = HashMap<String, MutableSet<String>>()
        for ((key, list) in guesses) list.forEach { g.getOrPut(normalizeName(it)) { HashSet() }.add(key) }
        guessed = g
    }

    /** Every medicine with a name the search box can offer. */
    val common: List<DrugEntry> by lazy { entries.filter { it.displayName.isNotBlank() } }

    fun entry(key: String): DrugEntry? = byKey[key]
    private val byKey by lazy { entries.associateBy { it.key } }

    /**
     * Everything the search box can suggest, normalized once (≈30k rows), so typing stays fast
     * even on the watch. English names point to the Spanish name ("acetaminophen" -> Paracetamol).
     */
    val searchIndex: List<SearchHit> by lazy {
        val out = LinkedHashMap<String, SearchHit>()
        fun add(raw: String, name: String, keys: Set<String>, kind: SearchHit.Kind) {
            val n = normalizeName(raw)
            if (n.isNotEmpty() && n !in out) out[n] = SearchHit(n, name, keys, kind)
        }
        for (e in entries) {
            val kind = if (e.popular) SearchHit.Kind.COMMON else SearchHit.Kind.INGREDIENT
            add(e.displayName, e.displayName, setOf(e.key), kind)
            if (e.english != e.displayName) add(e.english, e.displayName, setOf(e.key), kind)
        }
        for (e in entries) {
            for (a in e.aliases) {
                val keys = exact[normalizeName(a)] ?: setOf(e.key)
                val kind = when {
                    keys.size > 1 && GENERIC_COMBO.containsMatchIn(normalizeName(a)) -> SearchHit.Kind.COMBINATION
                    e.popular -> SearchHit.Kind.COMMON
                    else -> SearchHit.Kind.INGREDIENT
                }
                add(a, a, keys, kind)
            }
        }
        for (e in entries) {
            for (p in e.products) add(p, p, exact[normalizeName(p)] ?: setOf(e.key), SearchHit.Kind.BRAND)
        }
        out.values.toList()
    }

    /** Canonical ingredient keys for a typed name; empty when nothing is recognized. */
    fun ingredientsFor(name: String): Set<String> {
        val n = stripDose(normalizeName(name))
        if (n.isEmpty()) return emptySet()
        lookup(n)?.let { return it }

        // Combination products: "amoxicilina + clavulanico", "paracetamol/cafeina", "x y z".
        val parts = n.split(Regex("\\s*(\\+|/|,|\\by\\b|\\bcon\\b)\\s*")).map { it.trim() }.filter { it.isNotEmpty() }
        if (parts.size > 1) {
            val found = parts.flatMap { ingredientsFor(it) }.toSet()
            if (found.isNotEmpty()) return found
        }

        // Longest run of words that matches ("naproxeno sodico forte" -> naproxeno sodico / naproxeno).
        val words = n.split(' ')
        for (len in words.size downTo 1) {
            for (start in 0..words.size - len) {
                lookup(words.subList(start, start + len).joinToString(" "))?.let { return it }
            }
        }
        return emptySet()
    }

    private fun lookup(n: String): Set<String>? =
        exact[n] ?: guessed[n] ?: exact[stem(n)] ?: guessed[stem(n)] ?: stemIndex[stem(n)]

    /** Last-chance match ignoring the final vowel (ibuprofen ~ ibuprofeno, losartan ~ losartán). */
    private val stemIndex: Map<String, Set<String>> by lazy {
        val m = HashMap<String, MutableSet<String>>()
        (exact.entries + guessed.entries).forEach { (k, v) -> m.getOrPut(stem(k)) { HashSet() }.addAll(v) }
        m
    }

    private fun stem(s: String): String = if (s.length > 4 && s.last() in "aeo") s.dropLast(1) else s

    companion object {
        val EMPTY = DrugLexicon(emptyList())

        private val dose = Regex("\\b\\d+([.,]\\d+)?\\s*(mg|mcg|ug|µg|g|ml|ui|iu|%|gotas)?\\b")

        /** "Valsartan, amlodipino e hidroclorotiazida", "Ambrisentan y tadalafilo": generic combination names. */
        internal val GENERIC_COMBO = Regex(",| y | e ")

        internal fun stripDose(n: String): String =
            n.replace(dose, " ").replace(Regex("\\s+"), " ").trim()
    }
}
