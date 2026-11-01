package com.example.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.example.R

val HindSiliguriFont = FontFamily(
    Font(R.font.hind_siliguri, FontWeight.Normal),
    Font(R.font.hind_siliguri, FontWeight.Medium),
    Font(R.font.hind_siliguri, FontWeight.SemiBold),
    Font(R.font.hind_siliguri, FontWeight.Bold)
)

val PlusJakartaFont = FontFamily(
    Font(R.font.plus_jakarta_sans, FontWeight.Normal),
    Font(R.font.plus_jakarta_sans, FontWeight.SemiBold),
    Font(R.font.plus_jakarta_sans, FontWeight.Bold)
)

val Typography = Typography(
    headlineLarge = TextStyle(
        fontFamily = HindSiliguriFont,
        fontWeight = FontWeight.Bold,
        fontSize = 26.sp,
        lineHeight = 34.sp
    ),
    headlineMedium = TextStyle(
        fontFamily = HindSiliguriFont,
        fontWeight = FontWeight.Bold,
        fontSize = 22.sp,
        lineHeight = 30.sp
    ),
    titleLarge = TextStyle(
        fontFamily = HindSiliguriFont,
        fontWeight = FontWeight.Bold,
        fontSize = 19.sp,
        lineHeight = 26.sp
    ),
    titleMedium = TextStyle(
        fontFamily = HindSiliguriFont,
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        lineHeight = 23.sp
    ),
    bodyLarge = TextStyle(
        fontFamily = HindSiliguriFont,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 25.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = HindSiliguriFont,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 21.sp
    ),
    labelLarge = TextStyle(
        fontFamily = HindSiliguriFont,
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
        lineHeight = 20.sp
    ),
    labelMedium = TextStyle(
        fontFamily = PlusJakartaFont,
        fontWeight = FontWeight.SemiBold,
        fontSize = 12.sp,
        lineHeight = 16.sp
    )
)
