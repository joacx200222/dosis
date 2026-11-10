package app.dosis.core.search

import app.dosis.core.model.Medicine
import app.dosis.core.text.normalizeName

/** Accent-insensitive suggestions for the "¿Qué tomaste?" field. Recent medicines rank first. */
object MedicineSuggester {

    fun suggest(
        query: String,
        recent: List<Medicine>,
        catalog: List<Medicine>,
        limit: Int = 6,
    ): List<Medicine> {
        val q = normalizeName(query)
        if (q.isEmpty()) return recent.take(limit)

        fun score(m: Medicine): Int? {
            val n = normalizeName(m.name)
            return when {
                n.startsWith(q) -> 0
                n.split(' ').any { it.startsWith(q) } -> 1
                m.ingredients.any { it.startsWith(q) } -> 2
                q in n -> 3
                else -> null
            }
        }

        val seen = HashSet<String>()
        return (recent.map { it to 0 } + catalog.map { it to 1 })
            .mapNotNull { (m, origin) -> score(m)?.let { Triple(m, origin, it) } }
            .sortedWith(compareBy({ it.third }, { it.second }, { it.first.name }))
            .map { it.first }
            .filter { seen.add(normalizeName(it.displayName)) }
            .take(limit)
    }
}
