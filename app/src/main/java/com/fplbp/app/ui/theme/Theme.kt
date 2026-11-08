package com.fplbp.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/** App-specific semantic colors (debt, safe, provisional) and the chart palette. */
@Immutable
data class AppColors(
    val gold: Color,
    val debt: Color,
    val safe: Color,
    val provisional: Color,
    val muted: Color,
    val grid: Color,
    val chartSurface: Color,
    /** Top of the app background gradient (fades into the background color). */
    val bgTop: Color,
    /** Categorical series, fixed order (validated for CVD separation). */
    val series: List<Color>,
    /** Sequential ramp for the position heatmap (light -> strong). */
    val heat: List<Color>,
)

private val DarkApp = AppColors(
    gold = Color(0xFFE8B84A),
    debt = Color(0xFFFF7A6B),
    safe = Color(0xFF4ADE80),
    provisional = Color(0xFFF5A524),
    muted = Color(0xFF8A96A3),
    grid = Color(0xFF2A3440),
    chartSurface = Color(0xFF18202A),
    bgTop = Color(0xFF241F12),
    series = listOf(
        Color(0xFF3987E5), Color(0xFFD95926), Color(0xFF199E70), Color(0xFFC98500),
        Color(0xFFD55181), Color(0xFF008300), Color(0xFF9085E9), Color(0xFFE66767),
    ),
    heat = listOf(Color(0xFF223040), Color(0xFF4A3A3F), Color(0xFF7A4440), Color(0xFFB4533F), Color(0xFFE8664A)),
)

private val LightApp = AppColors(
    gold = Color(0xFFB07D10),
    debt = Color(0xFFD23F2F),
    safe = Color(0xFF16803C),
    provisional = Color(0xFFB86E00),
    muted = Color(0xFF5F6B78),
    grid = Color(0xFFE2E6EA),
    chartSurface = Color(0xFFFFFFFF),
    bgTop = Color(0xFFFFEFCC),
    series = listOf(
        Color(0xFF2A78D6), Color(0xFFEB6834), Color(0xFF1BAF7A), Color(0xFFEDA100),
        Color(0xFFE87BA4), Color(0xFF008300), Color(0xFF6250D6), Color(0xFFE34948),
    ),
    heat = listOf(Color(0xFFF1F3F5), Color(0xFFF9D9CF), Color(0xFFF3AE98), Color(0xFFE77B5F), Color(0xFFD23F2F)),
)

val LocalAppColors = staticCompositionLocalOf { DarkApp }

private val DarkScheme = darkColorScheme(
    primary = Color(0xFFE8B84A),
    onPrimary = Color(0xFF1E1600),
    primaryContainer = Color(0xFF3A2E10),
    onPrimaryContainer = Color(0xFFFFE2A0),
    secondary = Color(0xFF9DB4C8),
    background = Color(0xFF0F141A),
    onBackground = Color(0xFFE6EAEE),
    surface = Color(0xFF0F141A),
    onSurface = Color(0xFFE6EAEE),
    surfaceVariant = Color(0xFF1C242E),
    onSurfaceVariant = Color(0xFFB4BEC8),
    surfaceContainer = Color(0xFF18202A),
    surfaceContainerHigh = Color(0xFF1F2833),
    surfaceContainerLow = Color(0xFF141B23),
    outline = Color(0xFF3A4654),
    outlineVariant = Color(0xFF2A3440),
    error = Color(0xFFFF7A6B),
)

private val LightScheme = lightColorScheme(
    primary = Color(0xFF8A6100),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFE2A0),
    onPrimaryContainer = Color(0xFF2B1F00),
    secondary = Color(0xFF4D6275),
    background = Color(0xFFF6F7F9),
    onBackground = Color(0xFF151A20),
    surface = Color(0xFFF6F7F9),
    onSurface = Color(0xFF151A20),
    surfaceVariant = Color(0xFFE8ECF0),
    onSurfaceVariant = Color(0xFF46525E),
    surfaceContainer = Color(0xFFFFFFFF),
    surfaceContainerHigh = Color(0xFFEFF2F5),
    surfaceContainerLow = Color(0xFFFBFCFD),
    outline = Color(0xFFC3CBD3),
    outlineVariant = Color(0xFFE2E6EA),
    error = Color(0xFFD23F2F),
)

private val AppTypography = Typography().let { t ->
    t.copy(
        headlineMedium = t.headlineMedium.copy(fontWeight = FontWeight.Bold),
        titleLarge = t.titleLarge.copy(fontWeight = FontWeight.SemiBold),
        titleMedium = t.titleMedium.copy(fontWeight = FontWeight.SemiBold),
        labelSmall = t.labelSmall.copy(fontSize = 11.sp, letterSpacing = 0.4.sp),
    )
}

@Composable
fun FplTheme(dark: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalAppColors provides if (dark) DarkApp else LightApp) {
        MaterialTheme(colorScheme = if (dark) DarkScheme else LightScheme, typography = AppTypography, content = content)
    }
}

object App {
    val colors: AppColors @Composable get() = LocalAppColors.current
}
