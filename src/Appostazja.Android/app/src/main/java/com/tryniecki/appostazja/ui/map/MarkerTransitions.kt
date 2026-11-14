package com.tryniecki.appostazja.ui.map

import android.view.animation.OvershootInterpolator
import android.view.animation.PathInterpolator
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.Marker
import com.google.maps.android.compose.CameraPositionState
import kotlin.math.roundToInt

/**
 * Animates pins flying out of and into clusters.
 *
 * Every `Marker` setter is a synchronous call into the map renderer (1-3 ms each on a Pixel), so moving
 * dozens of markers frame by frame drops frames. Instead the pins are drawn as sprites in a Compose layer
 * above the map. New markers are created transparent and revealed after their sprite lands, one per frame;
 * the sprite stays on top a little longer because the map applies the change a few frames later.
 *
 * All methods run on the main thread.
 */
/** @param durationScale the system animator duration scale (developer options, battery saver); 0 disables motion. */
internal class MarkerTransitions(private val durationScale: () -> Float) {
    /**
     * [Move]: a pin flies out of the cluster it split from (or a cluster glides to its new center).
     * [Exit]: a pin flies into the cluster it merged into. [Pop]: that cluster appears once they arrive.
     * [Fade]: a marker that merely came into view (pan, new area, theme or data change).
     */
    enum class Kind { Move, Pop, Exit, Fade }

    class Sprite(
        val bitmap: ImageBitmap,
        val anchorX: Float,
        val anchorY: Float,
        val from: LatLng,
        val to: LatLng,
        val kind: Kind,
        val delayNanos: Long,
        val durationNanos: Long,
        /** Transparent marker revealed when the sprite lands; null for exit sprites. */
        val marker: Marker?,
        /** Same item set, recomputed center: shown from the first frame, without scaling. */
        val steady: Boolean = false,
    ) {
        var startNanos = UNSTARTED
        var landed = false
        var queuedForReveal = false
        var revealedAtNanos = UNSTARTED
    }

    private val sprites = ArrayList<Sprite>()
    private val revealQueue = ArrayDeque<Sprite>()
    private var accepting = false
    private var addedSinceFrame = false
    private var quietFrames = 0
    private var waitingSince = UNSTARTED

    /** Last frame time, read by the overlay's draw pass. */
    var frameNanos by mutableLongStateOf(0L)
        private set

    var running by mutableStateOf(false)
        private set

    val enabled: Boolean get() = durationScale() > 0f

    fun canAdd(): Boolean = enabled && accepting && sprites.size < MAX_SPRITES

    /** Starts a new transition; sprites of the previous one land at once. */
    fun begin() {
        landAll()
        accepting = true
    }

    fun add(sprite: Sprite) {
        sprites += sprite
        addedSinceFrame = true
        running = true
    }

    fun scaled(millis: Long): Long = (millis * 1_000_000L * durationScale()).toLong()

    /**
     * Ends every flight immediately (the camera started moving or a new clustering arrived). Landed sprites
     * keep covering their hidden markers until those are revealed, so nothing blinks.
     */
    fun landAll() {
        accepting = false
        waitingSince = UNSTARTED
        sprites.removeAll { it.kind == Kind.Exit }
        for (sprite in sprites) {
            sprite.landed = true
            if (sprite.marker != null && !sprite.queuedForReveal) {
                sprite.queuedForReveal = true
                revealQueue += sprite
            }
        }
    }

    /** Called when the renderer is replaced or the map goes away; markers are no longer ours to touch. */
    fun clear() {
        accepting = false
        sprites.clear()
        revealQueue.clear()
        running = false
    }

    internal fun onFrame(nanos: Long) {
        // The library creates markers in ~10 ms batches. Start every sprite together once it is done, so the
        // flight is not interrupted by those batches; until then sprites wait at their origin.
        quietFrames = if (addedSinceFrame) 0 else quietFrames + 1
        addedSinceFrame = false
        val waiting = sprites.any { !it.landed && it.startNanos == UNSTARTED }
        if (waiting && waitingSince == UNSTARTED) waitingSince = nanos
        if (waiting && (quietFrames >= 2 || nanos - waitingSince >= MAX_SETTLE_NANOS)) {
            for (sprite in sprites) if (sprite.startNanos == UNSTARTED) sprite.startNanos = nanos
            waitingSince = UNSTARTED
        }
        for (sprite in sprites) {
            if (!sprite.landed && sprite.startNanos != UNSTARTED &&
                nanos - sprite.startNanos >= sprite.delayNanos + sprite.durationNanos
            ) {
                sprite.landed = true
            }
            if (sprite.landed && sprite.marker != null && !sprite.queuedForReveal) {
                sprite.queuedForReveal = true
                revealQueue += sprite
            }
        }

        // One marker update costs 2-3 ms; the sprite already shows the pin, so one per frame is invisible.
        val budgetEnd = System.nanoTime() + REVEAL_BUDGET_NANOS
        while (revealQueue.isNotEmpty()) {
            val sprite = revealQueue.removeFirst()
            try {
                sprite.marker?.alpha = 1f
            } catch (_: RuntimeException) {
                // The renderer already removed this marker.
            }
            sprite.revealedAtNanos = nanos
            if (System.nanoTime() >= budgetEnd) break
        }

        sprites.removeAll { sprite ->
            when (sprite.kind) {
                Kind.Exit -> sprite.landed
                else -> sprite.revealedAtNanos != UNSTARTED && nanos - sprite.revealedAtNanos >= REVEAL_OVERLAP_NANOS
            }
        }
        frameNanos = nanos
        running = sprites.isNotEmpty() || revealQueue.isNotEmpty()
    }

    internal fun forEachSprite(block: (Sprite) -> Unit) = sprites.forEach(block)

    companion object {
        const val MOVE_MS = 340L
        const val EXIT_MS = 300L
        const val POP_MS = 260L
        const val SHIFT_MS = 220L
        const val FADE_MS = 200L
        private const val UNSTARTED = Long.MIN_VALUE
        private const val MAX_SPRITES = 160
        private const val REVEAL_BUDGET_NANOS = 2_000_000L
        private const val MAX_SETTLE_NANOS = 150_000_000L

        /** The map draws on its own thread and shows an alpha change a few frames later. */
        private const val REVEAL_OVERLAP_NANOS = 150_000_000L
    }
}

private val emphasized = PathInterpolator(0.2f, 0f, 0f, 1f)
private val exitEasing = PathInterpolator(0.3f, 0f, 0.8f, 0.15f)
private val overshoot = OvershootInterpolator(1.6f)

@Composable
internal fun MarkerTransitionOverlay(
    transitions: MarkerTransitions,
    cameraState: CameraPositionState,
    /** Must be the same contentPadding the GoogleMap uses, or sprites land off their markers. */
    mapPadding: PaddingValues,
    modifier: Modifier = Modifier,
) {
    LaunchedEffect(transitions.running) {
        while (transitions.running) withFrameNanos(transitions::onFrame)
    }
    LaunchedEffect(transitions, cameraState) {
        snapshotFlow { cameraState.isMoving }.collect { moving -> if (moving) transitions.landAll() }
    }

    val layoutDirection = LocalLayoutDirection.current
    Canvas(modifier.fillMaxSize()) {
        val now = transitions.frameNanos
        if (!transitions.running) return@Canvas
        // Following the live camera keeps landed sprites on their pins while the user drags the map.
        val camera = cameraState.position
        val projection = MapProjection(
            camera.target.latitude, camera.target.longitude, camera.zoom, camera.bearing,
            size.width, size.height, density,
            MapPaddingPx(
                mapPadding.calculateLeftPadding(layoutDirection).toPx(),
                mapPadding.calculateTopPadding().toPx(),
                mapPadding.calculateRightPadding(layoutDirection).toPx(),
                mapPadding.calculateBottomPadding().toPx(),
            ),
        )
        // Exits on top: pins fly out from under the cluster they leave.
        transitions.forEachSprite { if (it.kind != MarkerTransitions.Kind.Exit) drawSprite(it, now, projection) }
        transitions.forEachSprite { if (it.kind == MarkerTransitions.Kind.Exit) drawSprite(it, now, projection) }
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawSprite(
    sprite: MarkerTransitions.Sprite,
    now: Long,
    projection: MapProjection,
) {
            val t = when {
                sprite.landed -> 1f
                sprite.startNanos == Long.MIN_VALUE -> 0f
                else -> ((now - sprite.startNanos - sprite.delayNanos).toFloat() / sprite.durationNanos)
                    .coerceAtMost(1f)
            }
            // Until a flight starts, the cluster it leaves stands in for it.
            if (t < 0f || (t == 0f && sprite.kind != MarkerTransitions.Kind.Exit && !sprite.steady)) return

            val travel: Float
            val scale: Float
            val alpha: Float
            when (sprite.kind) {
                MarkerTransitions.Kind.Move -> {
                    travel = emphasized.getInterpolation(t)
                    scale = if (sprite.steady) 1f else 0.7f + 0.3f * travel
                    alpha = 1f
                }
                MarkerTransitions.Kind.Exit -> {
                    travel = exitEasing.getInterpolation(t)
                    scale = 1f - 0.35f * travel
                    alpha = (1f - (t - 0.55f) / 0.45f).coerceIn(0f, 1f)
                }
                MarkerTransitions.Kind.Pop -> {
                    travel = 1f
                    scale = 0.55f + 0.45f * overshoot.getInterpolation(t)
                    alpha = (t * 2.5f).coerceAtMost(1f)
                }
                MarkerTransitions.Kind.Fade -> {
                    travel = 1f
                    scale = 1f
                    alpha = emphasized.getInterpolation(t)
                }
            }

            val (fromX, fromY) = projection.toScreen(sprite.from.latitude, sprite.from.longitude)
            val (toX, toY) = projection.toScreen(sprite.to.latitude, sprite.to.longitude)
            val x = fromX + (toX - fromX) * travel
            val y = fromY + (toY - fromY) * travel
            val width = sprite.bitmap.width * scale
            val height = sprite.bitmap.height * scale
            drawImage(
                image = sprite.bitmap,
                dstOffset = IntOffset((x - width * sprite.anchorX).roundToInt(), (y - height * sprite.anchorY).roundToInt()),
                dstSize = IntSize(width.roundToInt().coerceAtLeast(1), height.roundToInt().coerceAtLeast(1)),
                alpha = alpha,
                filterQuality = FilterQuality.Medium,
            )
}
