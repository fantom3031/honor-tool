package com.fantom3031.honortool.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val BrandBlue = Color(0xFF2E6BE6)
private val BrandBlueDark = Color(0xFFA9C6FF)

private val LightColors = lightColorScheme(
    primary = BrandBlue,
    secondary = Color(0xFF4F6180),
    tertiary = Color(0xFF00A88F)
)

private val DarkColors = darkColorScheme(
    primary = BrandBlueDark,
    secondary = Color(0xFFB9C7E4),
    tertiary = Color(0xFF56DBC3)
)

@Composable
fun HonorToolTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)

        darkTheme -> DarkColors
        else -> LightColors
    }

    MaterialTheme(colorScheme = colorScheme, content = content)
}
