package com.symphony.music.ui

import android.os.SystemClock
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlin.math.min

/**
 * "Progressive motion", after ColorOS 17: when a page appears, its pieces arrive one after another
 * in a wave that starts from a point (the top of the page, or the finger that asked for it),
 * instead of all at once.
 */
class Cascade(val origin: Offset?) {
    val start: Long = SystemClock.uptimeMillis()
}

val LocalCascade = compositionLocalOf<Cascade?> { null }

/** A fresh wave for [content] each time [key] changes, rising from [origin] (root coordinates) when given. */
@Composable
fun Cascading(key: Any? = Unit, origin: Offset? = null, content: @Composable () -> Unit) {
    val cascade = remember(key) { Cascade(origin) }
    CompositionLocalProvider(LocalCascade provides cascade) { content() }
}

/**
 * Joins the wave: fades in, rises a little and settles on a soft spring, later the further it sits
 * from the wave's origin. Pieces that appear afterwards (when scrolling) simply show up.
 */
fun Modifier.cascade(): Modifier = composed {
    val cascade = LocalCascade.current ?: return@composed this
    val density = LocalDensity.current
    // Only what is on screen when the page opens takes part in the wave.
    val animate = remember(cascade) { SystemClock.uptimeMillis() - cascade.start < 450 }
    val progress = remember(cascade) { Animatable(if (animate) 0f else 1f) }
    var where by remember(cascade) { mutableStateOf<Offset?>(null) }
    if (animate) {
        LaunchedEffect(cascade, where) {
            val at = where ?: return@LaunchedEffect
            val distanceDp = with(density) {
                val from = cascade.origin ?: Offset(at.x, 0f)
                (at - from).getDistance().toDp().value
            }
            // About 45 ms per 100 dp of distance, never more than half a second.
            val wait = min(480f, distanceDp * 0.45f).toLong() - (SystemClock.uptimeMillis() - cascade.start)
            if (wait > 0) delay(wait)
            progress.animateTo(1f, spring(dampingRatio = 0.82f, stiffness = 260f))
        }
    }
    this
        .onPlaced { if (where == null) where = it.positionInRoot() }
        .graphicsLayer {
            val p = progress.value
            alpha = p.coerceIn(0f, 1f)
            translationY = (1f - p) * 22.dp.toPx()
            val s = 0.94f + 0.06f * p
            scaleX = s
            scaleY = s
        }
}
