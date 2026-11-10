package app.dosis.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import app.dosis.android.ui.DosisApp
import app.dosis.android.ui.theme.DosisTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            DosisTheme {
                DosisApp(AppGraph.journal)
            }
        }
    }
}
