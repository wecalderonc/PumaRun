package com.pumarun.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val PumaOrange = Color(0xFFFC5200)
val OnPaceGreen = Color(0xFF2ECC71)
val OffPaceRed = Color(0xFFFF5252)

private val DarkColors = darkColorScheme(
    primary = PumaOrange,
    onPrimary = Color.White,
    secondary = Color(0xFFFFB38A),
    secondaryContainer = Color(0xFF5A2A10),
    onSecondaryContainer = Color(0xFFFFDBCB),
    background = Color(0xFF121212),
    surface = Color(0xFF121212),
    surfaceVariant = Color(0xFF1F1F1F),
)

private val LightColors = lightColorScheme(
    primary = PumaOrange,
    onPrimary = Color.White,
    secondary = Color(0xFFB33A00),
    secondaryContainer = Color(0xFFFFDBCB),
    onSecondaryContainer = Color(0xFF3A0B00),
)

@Composable
fun PumaTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        content = content,
    )
}
