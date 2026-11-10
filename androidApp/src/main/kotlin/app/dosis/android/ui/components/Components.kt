package app.dosis.android.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.dosis.android.ui.theme.Ink
import app.dosis.core.interactions.Severity
import app.dosis.core.model.PillAppearance
import app.dosis.core.model.PillShape
import app.dosis.core.ui.DosisPalette

/** The signature pill drawing. [height] drives the size; width depends on the shape. */
@Composable
fun Pill(appearance: PillAppearance, height: Dp = 28.dp, modifier: Modifier = Modifier) {
    val primary = Color(DosisPalette.ink(appearance.primary))
    val secondary = Color(DosisPalette.ink(appearance.secondary ?: appearance.primary))
    val width = when (appearance.shape) {
        PillShape.CAPSULE -> height * 2
        PillShape.OVAL -> height * 1.8f
        else -> height
    }
    Canvas(modifier.size(width, height)) { drawPill(appearance.shape, primary, secondary) }
}

private fun DrawScope.drawPill(shape: PillShape, primary: Color, secondary: Color) {
    val r = size.height / 2
    val score = Color.White.copy(alpha = 0.9f)
    when (shape) {
        PillShape.CAPSULE -> {
            clipRect(right = size.width / 2) { drawRoundRect(primary, cornerRadius = CornerRadius(r)) }
            clipRect(left = size.width / 2) { drawRoundRect(secondary, cornerRadius = CornerRadius(r)) }
        }
        PillShape.OVAL -> {
            drawRoundRect(primary, cornerRadius = CornerRadius(r))
            drawLine(score, Offset(size.width * 0.3f, r), Offset(size.width * 0.7f, r), strokeWidth = 1.5f * density)
        }
        PillShape.LIQUID, PillShape.DROPS -> {
            drawRoundRect(primary, topLeft = Offset(size.width * 0.2f, 0f), size = Size(size.width * 0.6f, size.height), cornerRadius = CornerRadius(r * 0.5f))
        }
        PillShape.INJECTION -> {
            drawRoundRect(primary, topLeft = Offset(size.width * 0.35f, 0f), size = Size(size.width * 0.3f, size.height), cornerRadius = CornerRadius(r * 0.3f))
        }
        PillShape.ROUND -> {
            drawCircle(primary)
            drawLine(score, Offset(size.width * 0.3f, size.height * 0.7f), Offset(size.width * 0.7f, size.height * 0.3f), strokeWidth = 1.5f * density)
        }
    }
}

@Composable
fun SeverityTag(severity: Severity, modifier: Modifier = Modifier) {
    val bg = Color(DosisPalette.severity(severity))
    val fg = if (severity == Severity.MAJOR) Color.White else Ink.Text
    Text(
        text = listOf(severity.symbol, severity.label).filter { it.isNotEmpty() }.joinToString(" "),
        color = fg,
        style = MaterialTheme.typography.bodySmall,
        fontWeight = FontWeight.Bold,
        modifier = modifier
            .background(bg, RoundedCornerShape(50))
            .padding(horizontal = 8.dp, vertical = 2.dp),
    )
}

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        color = Ink.Blue,
        modifier = modifier.padding(top = 20.dp, bottom = 8.dp),
    )
}

@Composable
fun HairlineDivider() {
    Spacer(Modifier.fillMaxWidth().height(1.dp).background(Ink.PaperSubtle))
}

@Composable
fun MedicineRow(
    appearance: PillAppearance,
    title: String,
    subtitle: String?,
    leading: String? = null,
    trailing: @Composable (() -> Unit)? = null,
) {
    Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        if (leading != null) {
            Text(leading, style = MaterialTheme.typography.bodySmall, color = Ink.Muted, modifier = Modifier.width(48.dp))
        }
        Pill(appearance, height = 22.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = Ink.Muted)
        }
        trailing?.invoke()
    }
}
