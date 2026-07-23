package com.obdinsight.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = Color(0xFF00629B),
    secondary = Color(0xFF4C6472),
    tertiary = Color(0xFF9C4146),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF8DCDFF),
    secondary = Color(0xFFB4CCDA),
    tertiary = Color(0xFFFFB2B5),
)

@Composable
fun ObdInsightTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colors = if (darkTheme) DarkColors else LightColors
    MaterialTheme(colorScheme = colors, content = content)
}
