package at.aau.kuhhandel.app.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import at.aau.kuhhandel.app.R

val DinFontFamily =
    FontFamily(
        Font(R.font.din_regular, FontWeight.Normal),
    )

val Typography =
    Typography(
        headlineLarge =
            TextStyle(
                fontFamily = DinFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 32.sp,
            ),
        bodyLarge =
            TextStyle(
                fontFamily = DinFontFamily,
                fontWeight = FontWeight.Normal,
                fontSize = 16.sp,
            ),
    )
