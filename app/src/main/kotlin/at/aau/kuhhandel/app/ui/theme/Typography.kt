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
        displayLarge =
            TextStyle(
                fontFamily = DinFontFamily,
                fontWeight = FontWeight.Black,
                fontSize = 57.sp,
            ),
        displayMedium =
            TextStyle(
                fontFamily = DinFontFamily,
                fontWeight = FontWeight.Black,
                fontSize = 45.sp,
            ),
        displaySmall =
            TextStyle(
                fontFamily = DinFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 36.sp,
            ),
        headlineLarge =
            TextStyle(
                fontFamily = DinFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 32.sp,
            ),
        headlineMedium =
            TextStyle(
                fontFamily = DinFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 28.sp,
            ),
        headlineSmall =
            TextStyle(
                fontFamily = DinFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 24.sp,
            ),
        titleLarge =
            TextStyle(
                fontFamily = DinFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 22.sp,
            ),
        titleMedium =
            TextStyle(
                fontFamily = DinFontFamily,
                fontWeight = FontWeight.Normal,
                fontSize = 18.sp,
            ),
        titleSmall =
            TextStyle(
                fontFamily = DinFontFamily,
                fontWeight = FontWeight.Normal,
                fontSize = 14.sp,
            ),
        bodyLarge =
            TextStyle(
                fontFamily = DinFontFamily,
                fontWeight = FontWeight.Normal,
                fontSize = 16.sp,
            ),
        bodyMedium =
            TextStyle(
                fontFamily = DinFontFamily,
                fontWeight = FontWeight.Normal,
                fontSize = 14.sp,
            ),
        bodySmall =
            TextStyle(
                fontFamily = DinFontFamily,
                fontWeight = FontWeight.Normal,
                fontSize = 12.sp,
            ),
        labelLarge =
            TextStyle(
                fontFamily = DinFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
            ),
        labelMedium =
            TextStyle(
                fontFamily = DinFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp,
            ),
        labelSmall =
            TextStyle(
                fontFamily = DinFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 11.sp,
            ),
    )
