package at.aau.kuhhandel.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme =
    darkColorScheme(
        primary = DefaultPurple,
        secondary = DarkPurple,
        tertiary = LightPurple,
        background = PureWhite,
        surface = WhitePurple,
        onPrimary = Color.White,
        onBackground = Color.White,
    )

private val LightColorScheme =
    lightColorScheme(
        primary = DefaultPurple,
        secondary = DarkPurple,
        tertiary = LightPurple,
        background = PureWhite,
        surface = WhitePurple,
        onPrimary = Color.White,
        onBackground = DarkPurple,
    )

@Composable
fun AndroidAppTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content,
    )
}
