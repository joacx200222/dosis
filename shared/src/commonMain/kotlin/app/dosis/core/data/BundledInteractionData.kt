package app.dosis.core.data

import app.dosis.core.interactions.InteractionDatabase
import app.dosis.core.interactions.KnownInteraction
import app.dosis.core.interactions.Severity
import app.dosis.core.interactions.Substance
import app.dosis.core.text.normalizeName
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Everything the interaction engine needs, loaded from data/generated/interactions.tsv. */
class InteractionData(
    val database: InteractionDatabase,
    val lexicon: DrugLexicon,
    val builtOn: String = "",
)

/** Holds the data once it has loaded (the file is parsed off the main thread on each platform). */
class InteractionDataStore(initial: InteractionData? = null) {
    private val _data = MutableStateFlow(initial)
    val data: StateFlow<InteractionData?> = _data.asStateFlow()
    fun set(value: InteractionData) { _data.value = value }
}

/**
 * Compact database backed by sorted primitive arrays, so the 160k DDInter pairs fit
 * comfortably even on the watch. Keys are normalized English ingredient names.
 */
class BundledInteractionDatabase internal constructor(
    private val index: Map<String, Int>,
    private val pairKeys: LongArray,
    private val pairLevels: ByteArray,
    private val substances: Map<Long, KnownInteraction>,
    /** dosis curated pairs (tools/curated_pairs.tsv); they already include the DDInter level. */
    private val curatedPairs: Map<Long, KnownInteraction>,
    private val ddinterLabel: String,
) : InteractionDatabase {

    override fun drugDrug(a: String, b: String): KnownInteraction? {
        val ia = index[normalizeName(a)] ?: return null
        val ib = index[normalizeName(b)] ?: return null
        if (ia == ib) return null
        val key = pairKey(minOf(ia, ib), maxOf(ia, ib))
        curatedPairs[key]?.let { return it }
        val pos = indexOfKey(key)
        if (pos < 0) return null
        return when (pairLevels[pos].toInt()) {
            0 -> KnownInteraction(
                Severity.MODERATE,
                "DDInter registra esta combinación sin indicar la gravedad; por precaución se muestra como moderada.",
                ddinterLabel,
            )
            1 -> KnownInteraction(Severity.MINOR, "DDInter la registra como interacción leve.", ddinterLabel)
            2 -> KnownInteraction(Severity.MODERATE, "DDInter la registra como interacción moderada.", ddinterLabel)
            else -> KnownInteraction(Severity.MAJOR, "DDInter la registra como interacción grave.", ddinterLabel)
        }
    }

    override fun drugSubstance(ingredient: String, substance: Substance): KnownInteraction? {
        val i = index[normalizeName(ingredient)] ?: return null
        return substances[subKey(i, substance)]
    }

    private fun indexOfKey(key: Long): Int {
        var lo = 0
        var hi = pairKeys.size - 1
        while (lo <= hi) {
            val mid = (lo + hi) ushr 1
            val v = pairKeys[mid]
            when {
                v < key -> lo = mid + 1
                v > key -> hi = mid - 1
                else -> return mid
            }
        }
        return -1
    }

    internal companion object {
        fun pairKey(a: Int, b: Int): Long = (a.toLong() shl 32) or b.toLong()
        fun subKey(i: Int, s: Substance): Long = (i.toLong() shl 8) or s.ordinal.toLong()
    }
}

/** Parser for the format written by tools/build_interactions.py (see its docstring). */
object InteractionDataParser {

    private fun level(c: Char): Int = when (c) {
        '.' -> 0
        ',' -> 1
        ';' -> 2
        '!' -> 3
        else -> -1
    }

    private fun severity(c: Char): Severity = when (c) {
        ',' -> Severity.MINOR
        '!' -> Severity.MAJOR
        else -> Severity.MODERATE
    }

    fun parse(text: String): InteractionData {
        val sources = HashMap<String, String>()
        val entries = ArrayList<DrugEntry>(2048)
        val guesses = HashMap<String, List<String>>()
        var builtOn = ""
        val keys = ArrayList<Long>(1 shl 18)
        val levels = ArrayList<Byte>(1 shl 18)
        val subRows = ArrayList<List<String>>()
        val pairRows = ArrayList<List<String>>()
        val bySubstanceName = Substance.entries.associateBy { it.name }

        for (line in text.lineSequence()) {
            if (line.isEmpty()) continue
            when (line[0]) {
                '#' -> builtOn = line.split('\t').getOrElse(2) { "" }
                'S' -> line.split('\t').let { sources[it[1]] = it[2] }
                'D' -> {
                    val p = line.split('\t')
                    val english = p[1]
                    val key = normalizeName(english)
                    entries += DrugEntry(
                        key = key,
                        english = english,
                        spanish = p.getOrNull(2)?.takeIf { it.isNotBlank() },
                        aliases = p.getOrNull(3).orEmpty().split('|').filter { it.isNotBlank() },
                        popular = p.getOrNull(5) == "1",
                        products = p.getOrNull(6).orEmpty().split('|').filter { it.isNotBlank() },
                    )
                    p.getOrNull(4)?.split('|')?.filter { it.isNotBlank() }?.let { if (it.isNotEmpty()) guesses[key] = it }
                }
                'P' -> {
                    val tab2 = line.indexOf('\t', 2)
                    val a = line.substring(2, tab2).toInt(36)
                    var prev = a
                    var start = tab2 + 1
                    for (i in start until line.length) {
                        val lv = level(line[i])
                        if (lv >= 0) {
                            prev += line.substring(start, i).toInt(36)
                            keys += BundledInteractionDatabase.pairKey(a, prev)
                            levels += lv.toByte()
                            start = i + 1
                        }
                    }
                }
                'X' -> subRows += line.split('\t', limit = 6)
                'Q' -> pairRows += line.split('\t', limit = 6)
            }
        }

        val index = HashMap<String, Int>(entries.size * 2)
        entries.forEachIndexed { i, e -> if (e.key !in index) index[e.key] = i }

        val subs = HashMap<Long, KnownInteraction>(subRows.size * 2)
        for (p in subRows) {
            val sub = bySubstanceName[p[2]] ?: continue
            val label = p[4].split(',').mapNotNull { sources[it] }.joinToString(" + ")
            subs[BundledInteractionDatabase.subKey(p[1].toInt(), sub)] =
                KnownInteraction(severity(p[3][0]), p[5], label)
        }

        val curated = HashMap<Long, KnownInteraction>(pairRows.size * 2)
        for (p in pairRows) {
            val label = p[4].split(',').mapNotNull { sources[it] }.joinToString(" + ")
            curated[BundledInteractionDatabase.pairKey(p[1].toInt(), p[2].toInt())] =
                KnownInteraction(severity(p[3][0]), p[5], label)
        }

        // The build script writes pairs already sorted; only sort if a hand-edited file is not.
        var keyArr = keys.toLongArray()
        var lvlArr = levels.toByteArray()
        if ((1 until keyArr.size).any { keyArr[it - 1] >= keyArr[it] }) {
            val order = keyArr.indices.sortedBy { keyArr[it] }
            keyArr = LongArray(order.size) { keys[order[it]] }
            lvlArr = ByteArray(order.size) { levels[order[it]] }
        }

        val db = BundledInteractionDatabase(index, keyArr, lvlArr, subs, curated, sources["ddinter"] ?: "DDInter")
        return InteractionData(db, DrugLexicon(entries, guesses), builtOn)
    }
}
