package app.dosis.core.interactions

import app.dosis.core.model.Medicine

enum class Severity(val label: String, val symbol: String) {
    MINOR("Leve", ""),
    MODERATE("Moderada", "!"),
    MAJOR("Grave", "!!"),
}

enum class SubstanceGroup(val label: String) {
    SUBSTANCE("Alcohol y tabaco"),
    DRUG("Drogas"),
    FOOD("Comidas y bebidas"),
    SUPPLEMENT("Suplementos"),
}

/**
 * Non-medicine things that are ALWAYS shown, whether or not the user consumes them.
 * Names must match tools/build_interactions.py (SUBSTANCES).
 */
enum class Substance(val label: String, val group: SubstanceGroup) {
    ALCOHOL("Alcohol", SubstanceGroup.SUBSTANCE),
    TOBACCO("Tabaco", SubstanceGroup.SUBSTANCE),

    CANNABIS("Cannabis", SubstanceGroup.DRUG),
    COCAINE("Cocaína", SubstanceGroup.DRUG),
    AMPHETAMINES("Anfetaminas / speed / meth", SubstanceGroup.DRUG),
    MDMA("MDMA / éxtasis", SubstanceGroup.DRUG),
    MEPHEDRONE("Mefedrona / catinonas", SubstanceGroup.DRUG),
    KETAMINE("Ketamina", SubstanceGroup.DRUG),
    PCP("PCP", SubstanceGroup.DRUG),
    MXE("MXE (metoxetamina)", SubstanceGroup.DRUG),
    DXM("DXM en dosis altas", SubstanceGroup.DRUG),
    GHB("GHB / GBL", SubstanceGroup.DRUG),
    NITROUS("Óxido nitroso (globos)", SubstanceGroup.DRUG),
    POPPERS("Poppers", SubstanceGroup.DRUG),
    OPIOIDS("Opioides (heroína, fentanilo…)", SubstanceGroup.DRUG),
    BENZODIAZEPINES("Benzodiacepinas sin receta", SubstanceGroup.DRUG),
    LSD("LSD", SubstanceGroup.DRUG),
    MUSHROOMS("Hongos (psilocibina)", SubstanceGroup.DRUG),
    DMT("DMT", SubstanceGroup.DRUG),
    AYAHUASCA("Ayahuasca", SubstanceGroup.DRUG),
    MESCALINE("San Pedro / mescalina", SubstanceGroup.DRUG),
    TWO_C("2C-B / 2C-x", SubstanceGroup.DRUG),
    TWO_C_T("2C-T-x", SubstanceGroup.DRUG),
    NBOME("NBOMe (25I y otros)", SubstanceGroup.DRUG),
    DOX("DOx (DOM, DOC…)", SubstanceGroup.DRUG),
    FIVE_MEO("5-MeO-DMT y similares", SubstanceGroup.DRUG),
    AMT("αMT", SubstanceGroup.DRUG),

    GRAPEFRUIT("Toronja", SubstanceGroup.FOOD),
    DAIRY("Lácteos", SubstanceGroup.FOOD),
    CAFFEINE("Cafeína", SubstanceGroup.FOOD),

    SUPPLEMENTS("Suplementos y hierbas", SubstanceGroup.SUPPLEMENT),
}

data class KnownInteraction(
    val severity: Severity,
    val description: String,
    val source: String,
)

/** Read-only interaction data (DDInter, TripSit, curated table…). Keys are normalized ingredients. */
interface InteractionDatabase {
    fun drugDrug(a: String, b: String): KnownInteraction?
    fun drugSubstance(ingredient: String, substance: Substance): KnownInteraction?
}

/** "Con tu botiquín": [medicine] interacts with [other], which is saved in the cabinet. */
data class CabinetWarning(
    val other: Medicine,
    val ingredient: String,
    val otherIngredient: String,
    val interaction: KnownInteraction,
)

/** "Ten en cuenta siempre": one row per [Substance], never hidden. */
data class AlwaysWarning(
    val substance: Substance,
    /** Never below [Severity.MODERATE]. */
    val severity: Severity,
    /** False when no source had data; severity is then MODERATE as a precaution. */
    val hasData: Boolean,
    val description: String?,
    val source: String?,
)

data class InteractionReport(
    val medicine: Medicine,
    val withCabinet: List<CabinetWarning>,
    val alwaysConsider: List<AlwaysWarning>,
    /** False while the bundled data file is still loading; UIs say "Cargando datos…". */
    val dataReady: Boolean = true,
) {
    /** "Ten en cuenta siempre" grouped for display: groups in enum order, worst first inside each. */
    val alwaysByGroup: List<Pair<SubstanceGroup, List<AlwaysWarning>>>
        get() = alwaysConsider.groupBy { it.substance.group }.toList()
            .sortedBy { it.first.ordinal }
            .map { (g, ws) -> g to ws.sortedWith(compareByDescending<AlwaysWarning> { it.severity }.thenByDescending { it.hasData }) }

    /** Source labels used anywhere in this report, for the "Fuente:" line. */
    val sources: List<String>
        get() = (withCabinet.map { it.interaction.source } + alwaysConsider.mapNotNull { it.source })
            .flatMap { it.split(" + ") }.distinct()

    /** True when no substance row had real data (show the "sin datos específicos" line). */
    val noSubstanceData: Boolean get() = alwaysConsider.none { it.hasData }

    val highestSeverity: Severity
        get() = (withCabinet.map { it.interaction.severity } + alwaysConsider.map { it.severity }).maxOrNull()
            ?: Severity.MODERATE
}
