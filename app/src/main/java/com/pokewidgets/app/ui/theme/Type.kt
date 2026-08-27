package com.pokewidgets.app.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.pokewidgets.app.R

/**
 * Press Start 2P (SIL OFL). Deliberately used only for short headings, labels and
 * numerals — it is close to unreadable as body copy, so body text stays on the
 * platform default.
 */
val PixelFont = FontFamily(Font(R.font.press_start_2p, FontWeight.Normal))

/** Headline/label styles that opt into the pixel face. Body styles stay default. */
val PokeTypography = Typography().let { base ->
    base.copy(
        displaySmall = base.displaySmall.pixel(20.sp, 30.sp),
        headlineMedium = base.headlineMedium.pixel(16.sp, 26.sp),
        headlineSmall = base.headlineSmall.pixel(13.sp, 22.sp),
        titleMedium = base.titleMedium.pixel(11.sp, 18.sp),
        labelLarge = base.labelLarge.pixel(10.sp, 16.sp),
        labelSmall = base.labelSmall.pixel(8.sp, 13.sp),
    )
}

private fun TextStyle.pixel(size: androidx.compose.ui.unit.TextUnit, height: androidx.compose.ui.unit.TextUnit) =
    copy(
        fontFamily = PixelFont,
        fontWeight = FontWeight.Normal,
        fontSize = size,
        lineHeight = height,
        // The face is monospaced and already wide; negative tracking keeps headings compact.
        letterSpacing = 0.sp,
    )
