package app.dosis.android.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.dosis.android.ui.components.Pill
import app.dosis.android.ui.components.SectionLabel
import app.dosis.android.ui.components.SeverityTag
import app.dosis.android.ui.theme.Ink
import app.dosis.core.interactions.InteractionReport
import app.dosis.core.interactions.Severity
import app.dosis.core.journal.Journal
import app.dosis.core.model.Medicine
import app.dosis.core.time.hhmm
import app.dosis.core.time.nowLocal

/** Warnings are shown BEFORE confirming, on every platform. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LogDoseSheet(journal: Journal, medicine: Medicine, onDismiss: () -> Unit) {
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val at = remember { nowLocal() }
    var addToCabinet by remember { mutableStateOf(true) }
    val data by journal.interactionData.collectAsState()
    val report = remember(medicine, data) { journal.preview(medicine) }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheet, containerColor = Ink.Paper) {
        Column(
            Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 24.dp).padding(bottom = 32.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Pill(medicine.appearance, height = 28.dp)
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(medicine.displayName, style = MaterialTheme.typography.headlineMedium)
                    Text("Ahora · ${at.hhmm()}", style = MaterialTheme.typography.bodySmall, color = Ink.Muted)
                }
            }

            if (!medicine.inCabinet) {
                Row(Modifier.fillMaxWidth().padding(top = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Agregar a mi botiquín", modifier = Modifier.weight(1f))
                    Switch(
                        checked = addToCabinet,
                        onCheckedChange = { addToCabinet = it },
                        colors = SwitchDefaults.colors(checkedTrackColor = Ink.Pink),
                    )
                }
            }

            InteractionSections(report)

            Spacer(Modifier.height(24.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f)) { Text("Cancelar") }
                Button(
                    onClick = {
                        journal.logDose(medicine, at, addToCabinet = addToCabinet || medicine.inCabinet)
                        onDismiss()
                    },
                    modifier = Modifier.weight(1f),
                ) { Text("Registrar igual") }
            }
        }
    }
}

@Composable
fun InteractionSections(report: InteractionReport) {
    if (!report.dataReady) {
        Text(
            "Cargando datos de interacciones…",
            style = MaterialTheme.typography.bodySmall,
            color = Ink.Muted,
            modifier = Modifier.padding(top = 16.dp),
        )
    }
    if (report.withCabinet.isNotEmpty()) {
        SectionLabel("Con tu botiquín")
        report.withCabinet.forEach { w ->
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 4.dp)) {
                SeverityTag(w.interaction.severity)
                Spacer(Modifier.width(8.dp))
                Text("${report.medicine.name} + ${w.other.name}", style = MaterialTheme.typography.titleMedium)
            }
            Text(
                "${w.interaction.description} ${w.other.name} está en tu botiquín.",
                style = MaterialTheme.typography.bodySmall,
                color = Ink.Muted,
            )
        }
    }

    SectionLabel("Ten en cuenta siempre")
    if (report.noSubstanceData) {
        Text(
            "Sin datos específicos para esta medicina. Por precaución, considéralo al menos moderado.",
            style = MaterialTheme.typography.bodySmall,
            color = Ink.Muted,
        )
    }
    report.alwaysByGroup.forEach { (group, warnings) ->
        Text(
            group.label,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(top = 16.dp),
        )
        warnings.filter { it.hasData }.forEach { w ->
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 10.dp)) {
                SeverityTag(w.severity)
                Spacer(Modifier.width(8.dp))
                Text(w.substance.label, style = MaterialTheme.typography.titleMedium)
            }
            Text(w.description ?: "Consulta a tu farmacéutico.", style = MaterialTheme.typography.bodySmall, color = Ink.Muted)
        }
        // Every substance is still listed: the ones without data share one "al menos moderada" line.
        val noData = warnings.filterNot { it.hasData }
        if (noData.isNotEmpty() && !report.noSubstanceData) {
            Row(verticalAlignment = Alignment.Top, modifier = Modifier.padding(top = 10.dp)) {
                SeverityTag(Severity.MODERATE)
                Spacer(Modifier.width(8.dp))
                Column {
                    Text(noData.joinToString(" · ") { it.substance.label }, style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Sin datos específicos. Por precaución, considéralas al menos moderadas.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Ink.Muted,
                    )
                }
            }
        } else if (report.noSubstanceData) {
            Text(
                noData.joinToString(" · ") { it.substance.label },
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
    }
    if (report.sources.isNotEmpty()) {
        Text(
            "Fuente: ${report.sources.joinToString(" · ")}",
            style = MaterialTheme.typography.bodySmall,
            color = Ink.Muted,
            modifier = Modifier.padding(top = 12.dp),
        )
    }
}
