package app.dosis.android.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import app.dosis.core.ui.DosisPalette

object Ink {
    val Paper = Color(DosisPalette.Paper)
    val PaperSubtle = Color(DosisPalette.PaperSubtle)
    val Text = Color(DosisPalette.Ink)
    val Muted = Color(DosisPalette.InkMuted)
    val Pink = Color(DosisPalette.Pink)
    val Blue = Color(DosisPalette.Blue)
    val Yellow = Color(DosisPalette.Yellow)
}

// TODO(diseño): bundle a chunky grotesque (p. ej. una "Black" de Google Fonts) for headings.
private val Display = FontFamily.SansSerif

private val typography = Typography(
    displayLarge = TextStyle(fontFamily = Display, fontWeight = FontWeight.Black, fontSize = 44.sp, lineHeight = 44.sp, letterSpacing = (-0.03).em),
    headlineLarge = TextStyle(fontFamily = Display, fontWeight = FontWeight.Black, fontSize = 32.sp, lineHeight = 34.sp, letterSpacing = (-0.02).em),
    headlineMedium = TextStyle(fontFamily = Display, fontWeight = FontWeight.Black, fontSize = 24.sp, lineHeight = 28.sp),
    titleMedium = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 16.sp),
    bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 22.sp),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 20.sp),
    bodySmall = TextStyle(fontSize = 12.sp, lineHeight = 16.sp),
    labelSmall = TextStyle(fontWeight = FontWeight.Medium, fontSize = 11.sp, letterSpacing = 0.12.em),
)

@Composable
fun DosisTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = Ink.Blue,
            onPrimary = Color.White,
            secondary = Ink.Pink,
            onSecondary = Color.White,
            tertiary = Ink.Yellow,
            background = Ink.Paper,
            onBackground = Ink.Text,
            surface = Ink.Paper,
            onSurface = Ink.Text,
            surfaceVariant = Ink.PaperSubtle,
            onSurfaceVariant = Ink.Muted,
            outline = Ink.Blue,
            surfaceContainer = Ink.Paper,
            surfaceContainerLow = Ink.Paper,
            surfaceContainerHigh = Ink.Paper,
        ),
        typography = typography,
        content = content,
    )
}
