package com.pokewidgets.app.widget

import com.pokewidgets.app.catalog.BattleBackground
import com.pokewidgets.app.data.Scene
import com.pokewidgets.app.data.TrainerSide
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * Where the Pokémon and its trainer go inside the widget, and which part of a battle
 * background shows behind them.
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

    /** A spot as fractions of a width and height, 0..1 from the top left. */
    data class Point(val x: Double, val y: Double)

    /**
     * Where a battle is staged: the far platform the Pokémon stands on, and the near one the
     * trainer stands on, as fractions of the widget (for a trainer on the left).
     */
    data class Stage(val foe: Point, val player: Point)

    /** Used when there is no background to take platform positions from. */
    val OPEN_STAGE = Stage(foe = Point(0.72, 0.6), player = Point(0.25, 1.0))

    /**
     * @param trainerInFront the trainer overlaps the Pokémon and is drawn over it.
     * @param anchorBottom figures stand on the bottom of their box rather than floating in
     *   the middle of it, so they stand on the ground or the platform under them.
     */
    data class Layout(
        val pokemon: Box,
        val trainer: Box?,
        val trainerInFront: Boolean,
        val anchorBottom: Boolean,
    )

    /**
     * Where the ground is on scenery, as a fraction of the widget's height. Scenery has no
     * platforms, so figures simply stand here, a little way into the ground below the horizon.
     */
    const val GROUND = 0.86

    /**
     * @param stage where the battle platforms are in this widget, for a trainer standing on
     *   the left; a right-hand trainer mirrors it. Only [Scene.BATTLE] uses it.
     * @param onScenery the background is scenery: figures stand on its ground, at [GROUND],
     *   rather than floating in the middle of the sky.
     * @param onBattlefield the background is a battlefield with its platforms painted in: a
     *   Pokémon on its own stands on the far one, as it would in a battle, instead of floating
     *   in the middle of the picture beside it.
     */
    fun layout(
        scene: Scene,
        side: TrainerSide,
        width: Int,
        height: Int,
        stage: Stage = OPEN_STAGE,
        onScenery: Boolean = false,
        onBattlefield: Boolean = false,
    ): Layout {
        val onLeft = side == TrainerSide.LEFT
        fun mirror(box: Box) = if (onLeft) box else box.copy(left = width - box.right)
        val floor = if (onScenery) (height * GROUND).roundToInt() else height

        // The Pokémon's feet go a little below the platform's centre, the way the games plant
        // a sprite in its shadow rather than balance it on the rim.
        fun onFarPlatform(): Box {
            val footX = stage.foe.x * width
            val footY = (stage.foe.y + 0.06) * height
            val halfWidth = min(min(footX, width - footX), width * 0.3)
            val bottom = min(height.toDouble(), footY)
            val tall = min(bottom, height * 0.7)
            return Box(
                (footX - halfWidth).roundToInt(),
                (bottom - tall).roundToInt(),
                max(1, (halfWidth * 2).roundToInt()),
                max(1, tall.roundToInt()),
            )
        }

        return when (scene) {
            // Exactly the pre-trainer widget: the whole box, centred. On scenery the Pokémon
            // stands on the ground instead; on a battlefield, on the far platform painted into
            // the picture.
            Scene.SOLO -> when {
                onBattlefield -> Layout(onFarPlatform(), trainer = null, trainerInFront = false, anchorBottom = true)
                else -> Layout(
                    pokemon = Box(0, 0, width, floor),
                    trainer = null,
                    trainerInFront = false,
                    anchorBottom = onScenery,
                )
            }

            Scene.SIDE_BY_SIDE -> {
                val trainerW = (width * 0.42).roundToInt()
                val trainerTop = (height * 0.08).roundToInt()
                Layout(
                    pokemon = mirror(Box(trainerW, 0, width - trainerW, floor)),
                    trainer = mirror(Box(0, trainerTop, trainerW, floor - trainerTop)),
                    trainerInFront = false,
                    anchorBottom = true,
                )
            }

            // The opening of a battle: the trainer from behind, in the foreground on the near
            // platform, and the Pokémon standing on the far one.
            Scene.BATTLE -> {
                val pokemon = onFarPlatform()

                // Back sprites are square, so the trainer's box is square too: as wide as it is
                // tall where the widget allows, which keeps them from shrinking to a sliver in a
                // tall widget while not sprawling across a short wide one.
                val trainerTop = (height * 0.36).roundToInt()
                val trainerW = min(width * 0.6, (height - trainerTop).toDouble()).roundToInt()
                val centre = (stage.player.x * width).coerceIn(trainerW / 2.0, width - trainerW / 2.0)
                val trainer = Box((centre - trainerW / 2.0).roundToInt(), trainerTop, trainerW, height - trainerTop)

                Layout(
                    pokemon = mirror(pokemon),
                    trainer = mirror(trainer),
                    trainerInFront = true,
                    anchorBottom = true,
                )
            }
        }
    }

    /**
     * Which part of a battle background shows in a [boxW]×[boxH] widget, in image pixels,
     * and where its platforms land.
     *
     * A plain centre crop cut the far platform off entirely in tall widgets, leaving the
     * Pokémon standing on nothing. The crop is positioned so the far platform sits about
     * three quarters of the way across and a little above the middle, then clamped to the
     * image — the widest widgets still see the whole field.
     */
    fun battleFrame(background: BattleBackground, boxW: Int, boxH: Int): Pair<Box, Stage> {
        val scenery = background.sceneryBox()
        val foe = background.foe?.takeIf { it.size == 2 }?.let { Point(it[0], it[1]) } ?: OPEN_STAGE.foe
        val player = background.player?.takeIf { it.size == 2 }?.let { Point(it[0], it[1]) } ?: OPEN_STAGE.player

        val cover = coverCrop(scenery.width, scenery.height, boxW, boxH)
        val foeX = foe.x * scenery.width
        val foeY = foe.y * scenery.height
        val left = (foeX - cover.width * 0.74).roundToInt().coerceIn(0, scenery.width - cover.width)
        val top = (foeY - cover.height * 0.55).roundToInt().coerceIn(0, scenery.height - cover.height)
        val crop = Box(scenery.left + left, scenery.top + top, cover.width, cover.height)

        fun inCrop(p: Point) = Point(
            ((p.x * scenery.width - left) / cover.width).coerceIn(0.08, 0.92),
            ((p.y * scenery.height - top) / cover.height).coerceIn(0.2, 1.0),
        )
        return crop to Stage(inCrop(foe), inCrop(player))
    }

    /**
     * Which part of a scenery image shows: centred across, and low enough that the ground —
     * where everyone stands — stays in view when a wide, short widget can only show a band.
     */
    fun sceneryCrop(background: BattleBackground, boxW: Int, boxH: Int): Box {
        val cover = coverCrop(background.w, background.h, boxW, boxH)
        val top = ((background.h - cover.height) * 0.7).roundToInt().coerceIn(0, background.h - cover.height)
        return cover.copy(top = top)
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
     * The largest source rectangle with the widget's shape, centred: as much of the image
     * as fits without stretching it.
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
fun BattleBackground.sceneryBox(): SceneLayout.Box =
    crop?.takeIf { it.size == 4 }?.let { SceneLayout.Box(it[0], it[1], it[2], it[3]) }
        ?: SceneLayout.Box(0, 0, w, h)
