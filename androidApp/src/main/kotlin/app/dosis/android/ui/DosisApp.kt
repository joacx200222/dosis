package app.dosis.android.ui

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import app.dosis.android.ui.screens.BotiquinScreen
import app.dosis.android.ui.screens.HistorialScreen
import app.dosis.android.ui.screens.HoyScreen
import app.dosis.android.ui.theme.Ink
import app.dosis.core.journal.Journal

enum class Tab(val label: String) { HOY("Hoy"), HISTORIAL("Historial"), BOTIQUIN("Botiquín") }

/**
 * Phone: bottom bar, one column. Tablet (≥ 600 dp, e.g. Tab S11): navigation rail
 * and two panes. Same app, adaptive layout.
 */
@Composable
fun DosisApp(journal: Journal) {
    val state by journal.state.collectAsState()
    var tab by rememberSaveable { mutableStateOf(Tab.HOY) }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val expanded = maxWidth >= 600.dp
        val content: @Composable (Modifier) -> Unit = { m ->
            when (tab) {
                Tab.HOY -> HoyScreen(journal, state, twoPane = expanded, modifier = m)
                Tab.HISTORIAL -> HistorialScreen(state, twoPane = expanded, modifier = m)
                Tab.BOTIQUIN -> BotiquinScreen(journal, state, twoPane = expanded, modifier = m)
            }
        }

        if (expanded) {
            Scaffold { padding ->
                Row(Modifier.padding(padding).fillMaxSize()) {
                    NavigationRail(containerColor = Ink.Paper, header = {
                        Column(Modifier.padding(12.dp)) {
                            Text("dosis", style = MaterialTheme.typography.headlineMedium, color = Ink.Blue)
                            Spacer(Modifier.height(12.dp))
                            Button(onClick = { tab = Tab.HOY }) { Text("+ Registrar") }
                        }
                    }) {
                        Tab.entries.forEach { t ->
                            NavigationRailItem(
                                selected = tab == t,
                                onClick = { tab = t },
                                icon = { TabDot(tab == t) },
                                label = { Text(t.label) },
                                colors = NavigationRailItemDefaults.colors(
                                    indicatorColor = Ink.Pink,
                                    selectedTextColor = Ink.Text,
                                    unselectedTextColor = Ink.Blue,
                                ),
                            )
                        }
                    }
                    content(Modifier.weight(1f))
                }
            }
        } else {
            Scaffold(
                bottomBar = {
                    NavigationBar(containerColor = Ink.Paper) {
                        Tab.entries.forEach { t ->
                            NavigationBarItem(
                                selected = tab == t,
                                onClick = { tab = t },
                                icon = { TabDot(tab == t) },
                                label = { Text(t.label) },
                                colors = NavigationBarItemDefaults.colors(
                                    indicatorColor = Ink.Pink,
                                    selectedTextColor = Ink.Text,
                                    unselectedTextColor = Ink.Blue,
                                ),
                            )
                        }
                    }
                },
            ) { padding -> content(Modifier.padding(padding)) }
        }
    }
}

@Composable
private fun TabDot(selected: Boolean) {
    // Placeholder icon until the custom riso icon set is drawn.
    Text(if (selected) "●" else "○", color = if (selected) Color.White else Ink.Blue)
}
