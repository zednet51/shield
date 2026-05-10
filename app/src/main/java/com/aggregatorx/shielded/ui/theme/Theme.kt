package com.aggregatorx.shielded.ui.theme

import android.app.Activity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.material3.Typography
import androidx.core.view.WindowCompat

// ── Operative Palette ─────────────────────────────────────────────────────────
val NeonGreen      = Color(0xFF00FF41)
val NeonGreenDim   = Color(0xFF00CC33)
val NeonGreenFaint = Color(0x2200FF41)
val PureBlack      = Color(0xFF000000)
val SurfaceBlack   = Color(0xFF0A0A0A)
val CardBlack      = Color(0xFF111111)
val BorderGreen    = Color(0xFF003B0F)
val TextPrimary    = Color(0xFFE0FFE8)
val TextSecondary  = Color(0xFF7FBF8A)
val TextDim        = Color(0xFF3A5C40)
val AccentRed      = Color(0xFFFF3B3B)
val AccentAmber    = Color(0xFFFFB300)
val AccentCyan     = Color(0xFF00E5FF)

private val ShieldColorScheme = darkColorScheme(
    primary            = NeonGreen,
    onPrimary          = PureBlack,
    primaryContainer   = BorderGreen,
    onPrimaryContainer = NeonGreen,
    secondary          = NeonGreenDim,
    onSecondary        = PureBlack,
    background         = PureBlack,
    onBackground       = TextPrimary,
    surface            = SurfaceBlack,
    onSurface          = TextPrimary,
    surfaceVariant     = CardBlack,
    onSurfaceVariant   = TextSecondary,
    outline            = BorderGreen,
    error              = AccentRed,
    onError            = PureBlack,
)

val ShieldTypography = Typography(
    displayLarge  = TextStyle(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold,   fontSize = 32.sp, color = NeonGreen),
    titleLarge    = TextStyle(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold,   fontSize = 18.sp, color = NeonGreen),
    titleMedium   = TextStyle(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Medium, fontSize = 14.sp, color = TextPrimary),
    bodyLarge     = TextStyle(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Normal, fontSize = 14.sp, color = TextPrimary),
    bodyMedium    = TextStyle(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Normal, fontSize = 12.sp, color = TextSecondary),
    bodySmall     = TextStyle(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Normal, fontSize = 11.sp, color = TextDim),
    labelLarge    = TextStyle(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold,   fontSize = 12.sp, color = NeonGreen),
    labelMedium   = TextStyle(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Medium, fontSize = 11.sp, color = TextSecondary),
    labelSmall    = TextStyle(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Normal, fontSize = 10.sp, color = TextDim),
)

@Composable
fun ShieldTheme(content: @Composable () -> Unit) {
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = PureBlack.toArgb()
            window.navigationBarColor = PureBlack.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = false
        }
    }
    MaterialTheme(
        colorScheme = ShieldColorScheme,
        typography  = ShieldTypography,
        content     = content
    )
}
