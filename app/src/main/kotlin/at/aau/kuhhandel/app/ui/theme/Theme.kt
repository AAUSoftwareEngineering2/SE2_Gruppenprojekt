package at.aau.kuhhandel.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// --- Erdige Palette mit maximalem Kontrast ---
private val DarkBark = Color(0xFF3E2723) // Sehr dunkles Holzbraun (für Primär-Buttons)
private val DeepMeadow = Color(0xFF2E7D32) // Kräftiges Waldgrün (für Sekundär-Elemente)
private val LightSand = Color(0xFFFFF8E1) // Helles, warmes Creme (Hintergrund)
private val RichSoil = Color(0xFF1B120B) // Fast schwarzes Braun (für Texte auf Sand)
private val ContrastWhite = Color(0xFFFFFFFF) // Reinweiß (für Texte auf Braun/Grün)

private val FarmColorScheme =
    lightColorScheme(
        primary = DarkBark,
        onPrimary = ContrastWhite,
        secondary = DeepMeadow,
        onSecondary = ContrastWhite,
        background = LightSand,
        onBackground = RichSoil,
        surface = LightSand,
        onSurface = RichSoil,
    )

val FarmTypography =
    Typography(
        headlineLarge =
            TextStyle(
                fontFamily = FontFamily.Serif,
                fontWeight = FontWeight.Bold,
                fontSize = 46.sp,
                color = RichSoil,
            ),
        labelLarge =
            TextStyle(
                fontFamily = FontFamily.Serif,
                fontWeight = FontWeight.SemiBold,
                fontSize = 20.sp,
            ),
        bodyLarge =
            TextStyle(
                fontFamily = FontFamily.Serif,
                fontWeight = FontWeight.Normal,
                fontSize = 16.sp,
                color = RichSoil,
            ),
    )

@Composable
fun AndroidAppTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = FarmColorScheme,
        typography = FarmTypography,
        content = content,
    )
}
