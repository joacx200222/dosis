package app.dosis.core

import app.dosis.core.interactions.InMemoryInteractionDatabase
import app.dosis.core.interactions.InteractionEngine
import app.dosis.core.interactions.KnownInteraction
import app.dosis.core.interactions.Severity
import app.dosis.core.interactions.Substance
import app.dosis.core.model.Medicine
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class InteractionEngineTest {
    private val db = InMemoryInteractionDatabase(
        drugPairs = mapOf(
            ("naproxeno" to "ibuprofeno") to KnownInteraction(Severity.MODERATE, "AINE + AINE", "t"),
            ("ibuprofeno" to "warfarina") to KnownInteraction(Severity.MAJOR, "sangrado", "t"),
            ("ibuprofeno" to "loratadina") to KnownInteraction(Severity.MINOR, "leve", "t"),
        ),
        substances = mapOf(
            ("ibuprofeno" to Substance.ALCOHOL) to KnownInteraction(Severity.MAJOR, "sangrado", "t"),
            ("ibuprofeno" to Substance.CAFFEINE) to KnownInteraction(Severity.MINOR, "leve", "t"),
        ),
    )
    private val engine = InteractionEngine(db)

    private val ibu = Medicine("ibu", "Ibuprofeno", ingredients = setOf("ibuprofeno"))
    private val napro = Medicine("napro", "Naproxeno", ingredients = setOf("naproxeno"))
    private val warfa = Medicine("w", "Warfarina", ingredients = setOf("warfarina"))
    private val lora = Medicine("l", "Loratadina", ingredients = setOf("loratadina"))

    @Test
    fun everySubstanceIsAlwaysListed() {
        val report = engine.check(ibu, emptyList())
        assertEquals(Substance.entries.toSet(), report.alwaysConsider.map { it.substance }.toSet())
    }

    @Test
    fun substanceSeverityNeverBelowModerate() {
        val report = engine.check(ibu, emptyList())
        assertTrue(report.alwaysConsider.all { it.severity >= Severity.MODERATE })
        val caffeine = report.alwaysConsider.first { it.substance == Substance.CAFFEINE }
        assertEquals(Severity.MODERATE, caffeine.severity) // MINOR raised to MODERATE
        assertTrue(caffeine.hasData)
        assertEquals(Severity.MAJOR, report.alwaysConsider.first { it.substance == Substance.ALCOHOL }.severity)
    }

    @Test
    fun missingDataIsModerateAndFlagged() {
        val unknown = Medicine("x", "Algo raro")
        val report = engine.check(unknown, emptyList())
        assertTrue(report.noSubstanceData)
        assertTrue(report.alwaysConsider.all { !it.hasData && it.severity == Severity.MODERATE })
    }

    @Test
    fun cabinetCheckUsesWholeCabinetSortedBySeverity() {
        val report = engine.check(ibu, listOf(napro, warfa, lora))
        assertEquals(listOf("w", "napro", "l"), report.withCabinet.map { it.other.id })
        // Drug–drug keeps its real severity (no floor).
        assertEquals(Severity.MINOR, report.withCabinet.last().interaction.severity)
    }

    @Test
    fun ignoresItselfAndMedicinesOutsideCabinet() {
        val outside = napro.copy(inCabinet = false)
        val report = engine.check(ibu, listOf(ibu, outside))
        assertTrue(report.withCabinet.isEmpty())
    }

    @Test
    fun pairLookupIsOrderAndAccentInsensitive() {
        assertEquals(Severity.MODERATE, db.drugDrug("Ibuprofeno", "NAPROXENO")?.severity)
        assertFalse(db.drugDrug("ibuprofeno", "paracetamol") != null)
    }
}
