package com.alan.semaforosmx.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = Color(0xFF2E7D32),
    secondary = Color(0xFF66BB6A),
    tertiary = Color(0xFFFFC107),
    background = Color(0xFFF5F5F5),
    surface = Color.White,
    onPrimary = Color.White
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF66BB6A),
    secondary = Color(0xFF2E7D32),
    tertiary = Color(0xFFFFC107),
    background = Color(0xFF121212),
    surface = Color(0xFF1E1E1E),
    onPrimary = Color.Black
)

@Composable
fun SemaforosMxTheme(
    darkTheme: Boolean = false,
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        content = content
    )
}
