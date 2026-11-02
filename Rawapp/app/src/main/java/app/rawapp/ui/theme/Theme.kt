package app.rawapp.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

object RawappColors {
    val Background = Color(0xFF000000)
    val Accent = Color(0xFFE8B86D)
    val TextPrimary = Color(0xFFF5F5F7)
    val TextSecondary = Color(0x99F5F5F7)
    val Error = Color(0xFFFF6B5E)
    val GlassFill = Color(0x24FFFFFF)
    val GlassSheen = Color(0x1FFFFFFF)
    val GlassEdgeBright = Color(0x73FFFFFF)
    val GlassEdgeDim = Color(0x14FFFFFF)
}

private val colors = darkColorScheme(
    primary = RawappColors.Accent,
    onPrimary = Color.Black,
    background = RawappColors.Background,
    onBackground = RawappColors.TextPrimary,
    surface = RawappColors.Background,
    onSurface = RawappColors.TextPrimary,
    error = RawappColors.Error,
)

private val typography = Typography(
    titleLarge = TextStyle(fontSize = 28.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.4).sp),
    titleMedium = TextStyle(fontSize = 17.sp, fontWeight = FontWeight.SemiBold),
    bodyLarge = TextStyle(fontSize = 17.sp, lineHeight = 24.sp),
    bodyMedium = TextStyle(fontSize = 15.sp, lineHeight = 21.sp),
    labelMedium = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.2.sp),
)

@Composable
fun RawappTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = colors, typography = typography, content = content)
}
