package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Forced Dark Color Scheme for premium, minimalistic OLED look
private val DarkColorScheme = darkColorScheme(
    primary = Color.White,
    onPrimary = Color.Black,
    secondary = AppleBlue,
    onSecondary = Color.White,
    tertiary = SecondaryText,
    background = BlackBackground,
    onBackground = Color.White,
    surface = CardDarkSurface,
    onSurface = Color.White,
    surfaceVariant = DarkGreySurface,
    onSurfaceVariant = SecondaryText,
    outline = BorderColor
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = true, // Force dark theme by default
    dynamicColor: Boolean = false, // Keep consistent minimalistic identity
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        typography = Typography,
        content = content
    )
}
