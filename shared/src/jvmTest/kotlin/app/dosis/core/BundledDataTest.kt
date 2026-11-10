package app.dosis.core

import app.dosis.core.data.InteractionDataParser
import app.dosis.core.data.InteractionDataStore
import app.dosis.core.demo.DemoData
import app.dosis.core.interactions.InteractionEngine
import app.dosis.core.interactions.Severity
import app.dosis.core.interactions.Substance
import app.dosis.core.model.Medicine
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** Checks the real bundled file (data/generated/interactions.tsv) end to end. */
class BundledDataTest {
    private val data by lazy {
        val file = listOf("../data/generated/interactions.tsv", "data/generated/interactions.tsv")
            .map(::File).first { it.exists() }
        InteractionDataParser.parse(file.readText())
    }
    private val engine by lazy { InteractionEngine(data.database, data.lexicon) }

    private fun med(name: String) = Medicine(name, name, ingredients = data.lexicon.ingredientsFor(name))

    @Test
    fun parsesAllDdinterPairs() {
        assertNotNull(data.database.drugDrug("ibuprofen", "naproxen"))
        assertNotNull(data.database.drugDrug("Warfarin", "ACETYLSALICYLIC ACID"))
        // Missing from the public DDInter CSVs, filled by tools/curated_pairs.tsv:
        // Missing from the public DDInter CSVs, filled by tools/curated_pairs.tsv:
        assertEquals(Severity.MAJOR, data.database.drugDrug("sildenafil", "nitroglycerin")?.severity)
        assertEquals(Severity.MAJOR, data.database.drugDrug("Sertraline", "Tramadol")?.severity)
        assertEquals(Severity.MAJOR, data.database.drugDrug("phenelzine", "sertraline")?.severity)
        assertEquals(Severity.MODERATE, data.database.drugDrug("spironolactone", "enalapril")?.severity)
        assertEquals(Severity.MAJOR, data.database.drugDrug("Sertraline", "Tramadol")?.severity)
        assertEquals(Severity.MAJOR, data.database.drugDrug("phenelzine", "sertraline")?.severity)
        assertEquals(Severity.MODERATE, data.database.drugDrug("spironolactone", "enalapril")?.severity)
    }

    @Test
    fun spanishNamesBrandsAndDosesResolve() {
        val lx = data.lexicon
        assertEquals(setOf("ibuprofen"), lx.ingredientsFor("Ibuprofeno"))
        assertEquals(setOf("naproxen"), lx.ingredientsFor("Apronax 550 mg"))
        assertEquals(setOf("acetaminophen"), lx.ingredientsFor("paracetamol 500mg"))
        assertEquals(setOf("amoxicillin", "clavulanic acid"), lx.ingredientsFor("Amoxicilina + ácido clavulánico"))
        assertEquals(setOf("amoxicillin", "clavulanic acid"), lx.ingredientsFor("Augmentin"))
        assertEquals(setOf("losartan"), lx.ingredientsFor("losartan"))
        assertEquals(setOf("sertraline"), lx.ingredientsFor("Sertralina 50"))
        assertTrue(lx.ingredientsFor("xyz no existe").isEmpty())
    }

    @Test
    fun everySubstanceAndDrugIsAlwaysListed() {
        val report = engine.check(med("Loratadina"), emptyList())
        assertEquals(Substance.entries.size, report.alwaysConsider.size)
        assertTrue(report.alwaysConsider.all { it.severity >= Severity.MODERATE })
    }

    @Test
    fun knownDangerousCombinationsAreMajor() {
        fun sev(name: String, s: Substance) =
            engine.check(med(name), emptyList()).alwaysConsider.first { it.substance == s }.severity
        assertEquals(Severity.MAJOR, sev("Viagra", Substance.POPPERS))
        assertEquals(Severity.MAJOR, sev("Sertralina", Substance.AYAHUASCA))
        assertEquals(Severity.MAJOR, sev("Sertralina", Substance.DXM))
        assertEquals(Severity.MAJOR, sev("Metronidazol", Substance.ALCOHOL))
        assertEquals(Severity.MAJOR, sev("Simvastatina", Substance.GRAPEFRUIT))
        assertEquals(Severity.MAJOR, sev("Tramadol", Substance.MDMA))
        assertEquals(Severity.MAJOR, sev("Clonazepam", Substance.ALCOHOL))
    }

    @Test
    fun cabinetUsesRealPairs() {
        val journal = DemoData.journal(InteractionEngine(InteractionDataStore(data)))
        val report = journal.preview(DemoData.ibuprofeno)
        assertTrue(report.dataReady)
        assertTrue(report.withCabinet.any { it.other.id == "napro" })
    }

    @Test
    fun searchOffersCommonMedicinesAndBrands() {
        val journal = DemoData.journal(InteractionEngine(InteractionDataStore(data)))
        assertTrue(journal.suggest("sertra").any { it.name == "Sertralina" })
        assertTrue(journal.suggest("advil").any { it.name == "Advil" && it.ingredients == setOf("ibuprofen") })
        // Logging a suggestion from the data creates a real medicine with its ingredients.
        val pick = journal.suggest("clonaz").first { it.name == "Clonazepam" }
        journal.logDose(pick, kotlinx.datetime.LocalDateTime(2026, 11, 8, 22, 0), addToCabinet = true)
        val saved = journal.state.value.cabinet.first { it.name == "Clonazepam" }
        assertFalse(saved.id.startsWith("lex:"))
        assertEquals(setOf("clonazepam"), saved.ingredients)
    }

    @Test
    fun tldAcriptegaIsOneProductWithItsRules() {
        val tld = setOf("tenofovir disoproxil", "lamivudine", "dolutegravir")
        assertEquals(tld, data.lexicon.ingredientsFor("Acriptega"))
        assertEquals(tld, data.lexicon.ingredientsFor("TLD"))

        val journal = DemoData.journal(InteractionEngine(InteractionDataStore(data)))
        val pick = journal.suggest("acrip").first()
        assertEquals("Acriptega", pick.name)
        assertEquals(tld, pick.ingredients)

        val db = data.database
        assertEquals(Severity.MAJOR, db.drugDrug("dolutegravir", "rifampicin")?.severity)
        assertEquals(Severity.MAJOR, db.drugDrug("dolutegravir", "calcium carbonate")?.severity)
        assertEquals(Severity.MODERATE, db.drugDrug("tenofovir disoproxil", "ibuprofen")?.severity)

        val report = engine.check(pick, emptyList())
        assertEquals(Severity.MAJOR, report.alwaysConsider.first { it.substance == Substance.SUPPLEMENTS }.severity)
        assertTrue(report.alwaysConsider.first { it.substance == Substance.MDMA }.hasData)
    }

    @Test
    fun bigSpanishListWithOfficialNamesAndCombos() {
        val lx = data.lexicon
        assertTrue(lx.common.size > 3000, "common=${lx.common.size}")
        // Official CIMA names reach medicines that had no manual Spanish name.
        assertEquals(setOf("pentamidine"), lx.ingredientsFor("pentamidina"))
        assertEquals(setOf("ceftazidime"), lx.ingredientsFor("Ceftazidima"))
        // Salt names fold into the same medicine.
        assertEquals(setOf("metamizole"), lx.ingredientsFor("Metamizol sódico"))
        // CIMA combinations are one product.
        assertEquals(setOf("valsartan", "amlodipine", "hydrochlorothiazide"), lx.ingredientsFor("Valsartan, amlodipino e hidroclorotiazida"))
        assertEquals(setOf("scopolamine", "metamizole"), lx.ingredientsFor("Buscapina Compositum"))

        val journal = DemoData.journal(InteractionEngine(InteractionDataStore(data)))
        assertTrue(journal.suggest("ceftaz").any { it.name == "Ceftazidima" })
        assertTrue(journal.suggest("valsartan y amlo").any { it.ingredients == setOf("valsartan", "amlodipine") })
        // Common medicines come before rare ones and generic combinations.
        val am = journal.suggest("am")
        assertTrue(
            am.take(4).all { m -> m.ingredients.all { data.lexicon.entry(it)?.popular == true } },
            "am -> ${am.map { it.name }}",
        )
    }

    @Test
    fun registryBrandsAreSearchableWithTheirIngredients() {
        val journal = DemoData.journal(InteractionEngine(InteractionDataStore(data)))
        val stilnox = journal.suggest("stilno").first { it.name == "Stilnox" }
        assertEquals(setOf("zolpidem"), stilnox.ingredients)
        assertTrue(journal.isBrandName(stilnox))
        assertEquals(setOf("zolpidem"), data.lexicon.ingredientsFor("Ambien"))
        assertTrue(data.lexicon.searchIndex.size > 20_000, "index=${data.lexicon.searchIndex.size}")
        // Registry ATC codes can be wrong; the ingredient text wins (Enterogermina is Bacillus clausii).
        assertEquals(setOf("bacillus clausii"), data.lexicon.ingredientsFor("Enterogermina"))
        // "Carbonato de calcio equivalente a calcio": the salt, not a bare "calcio".
        assertEquals(setOf("calcium carbonate", "cholecalciferol"), data.lexicon.ingredientsFor("Caltrate"))
    }

    @Test
    fun unloadedDataStillWarnsAsModerate() {
        val journal = DemoData.journal(InteractionEngine(InteractionDataStore()))
        val report = journal.preview(DemoData.ibuprofeno)
        assertFalse(report.dataReady)
        assertTrue(report.alwaysConsider.all { it.severity == Severity.MODERATE && !it.hasData })
    }
}
