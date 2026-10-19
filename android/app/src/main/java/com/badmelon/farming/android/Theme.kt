package com.badmelon.farming.android

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = Color(0xFF4176E6),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE4EDFD),
    onPrimaryContainer = Color(0xFF0F2A5C),
    background = Color(0xFFFAFBFC),
    onBackground = Color(0xFF0F1115),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF0F1115),
    surfaceVariant = Color(0xFFF5F6F7),
    onSurfaceVariant = Color(0xFF61666B),
    outline = Color(0x1F000000),
    error = Color(0xFFD92D20)
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF6F9BF0),
    onPrimary = Color(0xFF0B1220),
    primaryContainer = Color(0xFF1F2C44),
    onPrimaryContainer = Color(0xFFD8E4FF),
    background = Color(0xFF0F1115),
    onBackground = Color(0xFFE8EAED),
    surface = Color(0xFF17191D),
    onSurface = Color(0xFFE8EAED),
    surfaceVariant = Color(0xFF1F2126),
    onSurfaceVariant = Color(0xFFADB2B8),
    outline = Color(0x33FFFFFF),
    error = Color(0xFFFF8A80)
)

@Composable
fun LifeAssistantTheme(mode: String, content: @Composable () -> Unit) {
    val dark = when (mode) {
        "dark" -> true
        "light" -> false
        else -> isSystemInDarkTheme()
    }
    MaterialTheme(
        colorScheme = if (dark) DarkColors else LightColors,
        typography = Typography(),
        content = content
    )
}
