package com.griddrop.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Teal = Color(0xFF0B7285)
private val TealDark = Color(0xFF0A5E6E)
private val Mint = Color(0xFF2F9E44)

private val LightColors = lightColorScheme(
    primary = Teal,
    secondary = Mint,
    tertiary = TealDark,
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF3BC9DB),
    secondary = Color(0xFF69DB7C),
    tertiary = Color(0xFF66D9E8),
)

@Composable
fun GridDropTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        content = content,
    )
}
