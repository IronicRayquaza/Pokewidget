package com.pokewidgets.app.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import com.pokewidgets.app.R

/**
 * Press Start 2P (SIL OFL). Deliberately used only for short headings, labels and
 * numerals — it is close to unreadable as body copy, so body text stays on the
 * platform default.
 */
val PixelFont = FontFamily(Font(R.font.press_start_2p, FontWeight.Normal))

/**
 * Two faces with one job each.
 *
 * The pixel face carries identity: titles, chips, buttons, dex numbers — everything the
 * eye lands on rather than reads. Body copy is the platform face at a medium weight,
 * which keeps it heavy enough to sit next to a 2dp ink outline without looking thin, and
 * legible enough to actually explain a setting.
 */
val PokeTypography = Typography().let { base ->
    base.copy(
        displaySmall = base.displaySmall.pixel(22.sp, 32.sp),
        headlineMedium = base.headlineMedium.pixel(18.sp, 28.sp),
        headlineSmall = base.headlineSmall.pixel(15.sp, 24.sp),
        titleMedium = base.titleMedium.pixel(12.sp, 20.sp),
        titleSmall = base.titleSmall.pixel(10.sp, 17.sp),
        labelLarge = base.labelLarge.pixel(11.sp, 18.sp),
        labelMedium = base.labelMedium.pixel(9.sp, 15.sp),
        labelSmall = base.labelSmall.pixel(8.sp, 13.sp),

        bodyLarge = base.bodyLarge.copy(fontWeight = FontWeight.Medium),
        bodyMedium = base.bodyMedium.copy(fontWeight = FontWeight.Medium),
        bodySmall = base.bodySmall.copy(fontWeight = FontWeight.Normal, lineHeight = 18.sp),
    )
}

private fun TextStyle.pixel(size: TextUnit, height: TextUnit) = copy(
    fontFamily = PixelFont,
    fontWeight = FontWeight.Normal,
    fontSize = size,
    lineHeight = height,
    // The face is monospaced and already wide; any tracking on top of that pushes short
    // labels past the width of the pill they sit in.
    letterSpacing = 0.sp,
)
