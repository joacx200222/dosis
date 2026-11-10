package app.dosis.core.ui

import app.dosis.core.interactions.Severity
import app.dosis.core.model.InkColor

/** Approved risograph palette (ARGB). Shared by phone, tablet, watch and web. */
object DosisPalette {
    const val Paper = 0xFFFFFFFF
    const val PaperSubtle = 0xFFF4F5F7
    const val Ink = 0xFF111111
    const val InkMuted = 0xFF6B6F76
    const val Pink = 0xFFFF48B0
    const val Blue = 0xFF0078BF
    const val Yellow = 0xFFFFE800
    const val LightBlue = 0xFFCDE6F5
    const val WatchBackground = 0xFF0B0B0C

    fun ink(color: InkColor): Long = when (color) {
        InkColor.PINK -> Pink
        InkColor.BLUE -> Blue
        InkColor.YELLOW -> Yellow
        InkColor.BLACK -> Ink
        InkColor.GRAY -> InkMuted
    }

    fun severity(s: Severity): Long = when (s) {
        Severity.MAJOR -> Pink
        Severity.MODERATE -> Yellow
        Severity.MINOR -> LightBlue
    }
}
