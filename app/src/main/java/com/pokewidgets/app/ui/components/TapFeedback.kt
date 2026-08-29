package com.pokewidgets.app.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer

/**
 * The app's touch feedback for sprites and sprite cards.
 *
 * Pokémon sprites are pixel art on a transparent background, so the usual Material
 * affordances land badly: a ripple washes over the empty half of the cell rather than the
 * creature, and a plain `clickable` gives a 40x30 icon no visible response at all. What
 * reads correctly on a sprite is the language the games themselves use — the thing
 * squashes when you press it and springs back when you let go.
 *
 * Every animation here is a `graphicsLayer` transform, so nothing re-lays-out and nothing
 * re-decodes; the bitmap is drawn once and the compositor does the rest.
 */

/**
 * Shrinks while held and springs back on release.
 *
 * The spring is deliberately bouncy: an over-damped return feels like the UI is
 * recovering from the touch, while a slight overshoot feels like the sprite is reacting
 * to it.
 *
 * @param interactionSource must be the same one given to the `clickable` that owns the
 *   press, otherwise the scale never sees the gesture.
 */
@Composable
fun Modifier.pressScale(
    interactionSource: MutableInteractionSource,
    pressedScale: Float = 0.92f,
): Modifier {
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) pressedScale else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMediumLow,
        ),
        label = "pressScale",
    )
    return graphicsLayer {
        scaleX = scale
        scaleY = scale
    }
}

/**
 * A squash-and-stretch hop, replayed whenever [trigger] changes.
 *
 * Pair it with the cry: the sound and the movement are one event, and a sprite that only
 * makes a noise reads as a bug on a muted phone. Anchored at the bottom of the sprite so
 * it compresses against the ground and launches upward, rather than scaling about its
 * middle like a UI element.
 *
 * @param trigger any value that changes once per hop — a counter incremented on each tap.
 *   The initial composition does not animate.
 */
@Composable
fun Modifier.cryHop(trigger: Int): Modifier {
    val squash = remember { Animatable(1f) }
    val lift = remember { Animatable(0f) }

    LaunchedEffect(trigger) {
        if (trigger == 0) return@LaunchedEffect
        // Anticipation: compress fast, because the wind-up is what sells the launch.
        squash.animateTo(0.82f, tween(durationMillis = 70))
        // Release: stretch and rise together, then settle on a bouncy spring.
        lift.animateTo(
            targetValue = -0.16f,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioLowBouncy,
                stiffness = Spring.StiffnessMedium,
            ),
        )
        lift.animateTo(0f, spring(dampingRatio = Spring.DampingRatioLowBouncy))
        squash.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy))
    }

    return graphicsLayer {
        // Volume is conserved: what the sprite loses in height it gains in width, which
        // is what separates a squash from a shrink.
        scaleY = squash.value
        scaleX = 2f - squash.value
        translationY = lift.value * size.height
        transformOrigin = TransformOrigin(pivotFractionX = 0.5f, pivotFractionY = 1f)
    }
}
