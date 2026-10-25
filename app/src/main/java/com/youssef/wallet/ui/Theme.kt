package com.youssef.wallet.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val Green = Color(0xFF16A34A)
val Red = Color(0xFFDC2626)
val ChartColors = listOf(
    Color(0xFF0F766E), Color(0xFF7C3AED), Color(0xFFDC2626), Color(0xFF2563EB), Color(0xFFD97706),
    Color(0xFF059669), Color(0xFFDB2777), Color(0xFF0891B2), Color(0xFF65A30D), Color(0xFF9333EA)
)

private val Light = lightColorScheme(
    primary = Color(0xFF0F766E), onPrimary = Color.White, secondary = Color(0xFF7C3AED),
    tertiary = Color(0xFFB45309), background = Color(0xFFF6F8F8), surface = Color.White,
    surfaceVariant = Color(0xFFE6F0EF), error = Color(0xFFDC2626)
)
private val Dark = darkColorScheme(
    primary = Color(0xFF2DD4BF), onPrimary = Color(0xFF00201C), secondary = Color(0xFFA78BFA),
    tertiary = Color(0xFFFBBF24), background = Color(0xFF0E1514), surface = Color(0xFF16201F),
    surfaceVariant = Color(0xFF223130), error = Color(0xFFF87171)
)

@Composable
fun WalletTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (isSystemInDarkTheme()) Dark else Light, content = content)
}
