package com.tradevision.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColors = darkColorScheme(
    primary = Color(0xFF3D8BFF),
    secondary = Color(0xFF00C9A7),
    background = Color(0xFF0E1116),
    surface = Color(0xFF161B22),
)

private val LightColors = lightColorScheme(
    primary = Color(0xFF0066CC),
    secondary = Color(0xFF008A72),
)

@Composable
fun TradeVisionTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        content = content,
    )
}