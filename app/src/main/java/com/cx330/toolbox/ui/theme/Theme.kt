package com.cx330.toolbox.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color

private val Cream = Color(0xFFFBF6EC)
private val Paper = Color(0xFFF3E8D6)
private val CardWhite = Color(0xFFFFFDF8)
private val Ink = Color(0xFF2F2A26)
private val InkSoft = Color(0xFF6B5D4F)
private val Caramel = Color(0xFF8A5A3B)
private val Matcha = Color(0xFF8FA97E)
private val Yuzu = Color(0xFFE9A25B)
private val Danger = Color(0xFFC96A5B)
private val Border = Color(0xFFE4D5BE)

private val LightColors = lightColorScheme(
    primary = Caramel,
    onPrimary = CardWhite,
    primaryContainer = Paper,
    onPrimaryContainer = Ink,
    secondary = Matcha,
    onSecondary = Ink,
    tertiary = Yuzu,
    background = Cream,
    onBackground = Ink,
    surface = CardWhite,
    onSurface = Ink,
    surfaceVariant = Paper,
    onSurfaceVariant = InkSoft,
    outline = Border,
    error = Danger,
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFB57A52),
    onPrimary = Color(0xFF211C18),
    primaryContainer = Color(0xFF3A2F27),
    onPrimaryContainer = Color(0xFFF5EAD9),
    secondary = Color(0xFF9DBE8B),
    onSecondary = Color(0xFF211C18),
    tertiary = Color(0xFFE0A05E),
    background = Color(0xFF211C18),
    onBackground = Color(0xFFF5EAD9),
    surface = Color(0xFF2E2823),
    onSurface = Color(0xFFF5EAD9),
    surfaceVariant = Color(0xFF3A2F27),
    onSurfaceVariant = Color(0xFFBFB2A2),
    outline = Color(0xFF3E352D),
    error = Color(0xFFD98A7A),
)

@Composable
fun Cx330toolboxTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    SideEffect { JournalColors.apply(darkTheme) }

    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = Typography,
        content = content,
    )
}
