package com.pokewidgets.app.widget

import com.pokewidgets.app.data.Scene
import com.pokewidgets.app.data.TrainerSide
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * Where the Pokémon and its trainer go inside the widget.
 *
 * Pure arithmetic on pixel sizes, shared by the widget renderer and the in-app preview, so
 * the preview can never disagree with what lands on the home screen — and so the rules are
 * covered by JVM tests.
 */
object SceneLayout {

    data class Box(val left: Int, val top: Int, val width: Int, val height: Int) {
        val right: Int get() = left + width
        val bottom: Int get() = top + height
    }

    /**
     * @param trainerInFront the trainer overlaps the Pokémon and is drawn over it.
     * @param anchorBottom figures stand on the bottom of their box rather than floating in
     *   the middle of it, so two of them side by side share a floor.
     */
    data class Layout(
        val pokemon: Box,
        val trainer: Box?,
        val trainerInFront: Boolean,
        val anchorBottom: Boolean,
    )

    fun layout(scene: Scene, side: TrainerSide, width: Int, height: Int): Layout {
        val whole = Box(0, 0, width, height)
        val onLeft = side == TrainerSide.LEFT
        fun mirror(box: Box) = if (onLeft) box else box.copy(left = width - box.right)

        return when (scene) {
            // Exactly the pre-trainer widget: the whole box, centred.
            Scene.SOLO -> Layout(whole, trainer = null, trainerInFront = false, anchorBottom = false)

            Scene.SIDE_BY_SIDE -> {
                val trainerW = (width * 0.42).roundToInt()
                Layout(
                    pokemon = mirror(Box(trainerW, 0, width - trainerW, height)),
                    trainer = mirror(Box(0, (height * 0.08).roundToInt(), trainerW, height - (height * 0.08).roundToInt())),
                    trainerInFront = false,
                    anchorBottom = true,
                )
            }

            // The opening of a battle: the trainer from behind, large and in the foreground on
            // one side, the Pokémon they are about to send out standing further back.
            Scene.BATTLE -> {
                val trainerW = (width * 0.55).roundToInt()
                val trainerTop = (height * 0.38).roundToInt()
                val pokemonLeft = (width * 0.30).roundToInt()
                Layout(
                    pokemon = mirror(Box(pokemonLeft, 0, width - pokemonLeft, (height * 0.86).roundToInt())),
                    trainer = mirror(Box(0, trainerTop, trainerW, height - trainerTop)),
                    trainerInFront = true,
                    anchorBottom = true,
                )
            }
        }
    }

    /**
     * Where an image of [imageW]×[imageH] lands when fitted into [box]: as large as fits,
     * standing on the box's floor if [anchorBottom], centred otherwise.
     */
    fun fit(imageW: Int, imageH: Int, box: Box, anchorBottom: Boolean): Box {
        if (imageW <= 0 || imageH <= 0) return box
        val scale = min(box.width.toDouble() / imageW, box.height.toDouble() / imageH)
        val w = max(1, (imageW * scale).roundToInt())
        val h = max(1, (imageH * scale).roundToInt())
        val left = box.left + (box.width - w) / 2
        val top = if (anchorBottom) box.bottom - h else box.top + (box.height - h) / 2
        return Box(left, top, w, h)
    }

    /**
     * The source rectangle of a background that fills a [boxW]×[boxH] widget without
     * stretching: as much of the image as matches the widget's shape, centred.
     */
    fun coverCrop(srcW: Int, srcH: Int, boxW: Int, boxH: Int): Box {
        if (srcW <= 0 || srcH <= 0 || boxW <= 0 || boxH <= 0) return Box(0, 0, max(srcW, 0), max(srcH, 0))
        val srcAspect = srcW.toDouble() / srcH
        val boxAspect = boxW.toDouble() / boxH
        return if (srcAspect > boxAspect) {
            val w = (srcH * boxAspect).roundToInt().coerceIn(1, srcW)
            Box((srcW - w) / 2, 0, w, srcH)
        } else {
            val h = (srcW / boxAspect).roundToInt().coerceIn(1, srcH)
            Box(0, (srcH - h) / 2, srcW, h)
        }
    }

    /**
     * The largest bitmap of the widget's shape that costs at most [maxBytes], never bigger
     * than the widget itself. The ImageView stretches it the rest of the way; a background
     * does not need to be sharp, and every byte it saves is a byte the sprite can use.
     */
    fun plateSize(boxW: Int, boxH: Int, maxBytes: Long): Pair<Int, Int> {
        val full = boxW.toLong() * boxH * 4L
        if (full <= maxBytes || full <= 0) return max(1, boxW) to max(1, boxH)
        val f = sqrt(maxBytes.toDouble() / full)
        return max(1, (boxW * f).toInt()) to max(1, (boxH * f).toInt())
    }
}

/** The scenery rectangle of a battle background, in image pixels. */
fun com.pokewidgets.app.catalog.BattleBackground.sceneryBox(): SceneLayout.Box =
    crop?.takeIf { it.size == 4 }?.let { SceneLayout.Box(it[0], it[1], it[2], it[3]) }
        ?: SceneLayout.Box(0, 0, w, h)
