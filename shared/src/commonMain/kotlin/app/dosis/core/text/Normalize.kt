package app.dosis.core.text

private val accents = mapOf(
    'á' to 'a', 'é' to 'e', 'í' to 'i', 'ó' to 'o', 'ú' to 'u', 'ü' to 'u', 'ñ' to 'n',
    'à' to 'a', 'è' to 'e', 'ì' to 'i', 'ò' to 'o', 'ù' to 'u',
)

/** Lowercase, accent-free, single-spaced form used to match names and ingredients. */
fun normalizeName(raw: String): String =
    raw.trim()
        .lowercase()
        .map { accents[it] ?: it }
        .joinToString("")
        .replace(Regex("\\s+"), " ")
