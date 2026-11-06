package com.ehviewer.core.ui.component

import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.foundation.OverscrollEffect
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.pointer.PointerEvent
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.PointerInputModifierNode
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.sign
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/** One vertical edge effect: bounded translation, critical damping, no second Stretch effect. */
@Composable
fun rememberElasticOverscrollEffect(): OverscrollEffect {
    val scope = rememberCoroutineScope()
    val limit = with(LocalDensity.current) { 48.dp.toPx() }
    return remember(scope, limit) { ElasticOverscrollEffect(scope, limit) }
}

@Stable
private class ElasticOverscrollEffect(private val scope: CoroutineScope, private val limit: Float) : OverscrollEffect {
    private var displacement by mutableFloatStateOf(0f)
    private var returnAnimation: Job? = null
    private var touchGesture = false

    override val isInProgress: Boolean get() = displacement != 0f

    // Read state only while drawing. Neither composition, measurement nor pointer coordinates move.
    override val node: Modifier.Node = object : Modifier.Node(), DrawModifierNode, PointerInputModifierNode {
        override fun ContentDrawScope.draw() {
            val offset = displacement
            if (offset == 0f) {
                drawContent()
            } else {
                clipRect {
                    translate(top = offset) { this@draw.drawContent() }
                }
            }
        }

        override fun onPointerEvent(pointerEvent: PointerEvent, pass: PointerEventPass, bounds: IntSize) {
            if (pass != PointerEventPass.Initial) return
            touchGesture = pointerEvent.changes.any { it.type == PointerType.Touch || it.type == PointerType.Stylus }
            if (touchGesture && pointerEvent.changes.any { it.pressed && !it.previousPressed }) stopReturn()
            if (touchGesture && pointerEvent.changes.none { it.pressed } && displacement != 0f) returnToRest(0f)
        }

        override fun onCancelPointerInput() {
            touchGesture = false
            returnToRest(0f)
        }

        override fun onDetach() {
            stopReturn()
            displacement = 0f
        }
    }

    override fun applyToScroll(delta: Offset, source: NestedScrollSource, performScroll: (Offset) -> Offset): Offset {
        if (source != NestedScrollSource.UserInput || !touchGesture) return performScroll(delta)
        stopReturn()
        var released = 0f
        if (displacement * delta.y < 0f) {
            released = sign(delta.y) * min(abs(delta.y), abs(displacement) / DRAG_RESISTANCE)
            displacement += released * DRAG_RESISTANCE
        }
        val remaining = delta - Offset(0f, released)
        val consumed = performScroll(remaining)
        val edge = remaining.y - consumed.y
        if (edge != 0f) {
            val resistance = DRAG_RESISTANCE * (1f - abs(displacement) / limit).coerceAtLeast(0f)
            displacement = (displacement + edge * resistance).coerceIn(-limit, limit)
        }
        return consumed + Offset(0f, released + edge)
    }

    override suspend fun applyToFling(velocity: Velocity, performFling: suspend (Velocity) -> Velocity) {
        val remaining = velocity - performFling(velocity)
        if (touchGesture) returnToRest(remaining.y)
    }

    private fun stopReturn() {
        returnAnimation?.cancel()
        returnAnimation = null
    }

    private fun returnToRest(remainingVelocity: Float) {
        stopReturn()
        if (displacement == 0f && remainingVelocity == 0f) return
        // A stretched edge returns monotonically. A fling arriving at rest gets a small impulse.
        val velocity = if (displacement == 0f) (remainingVelocity * 0.14f).coerceIn(-limit * 10, limit * 10) else 0f
        returnAnimation = scope.launch {
            animate(
                initialValue = displacement,
                targetValue = 0f,
                initialVelocity = velocity,
                animationSpec = spring(dampingRatio = 1f, stiffness = 350f, visibilityThreshold = 0.5f),
            ) { value, _ -> displacement = value.coerceIn(-limit, limit) }
            displacement = 0f
        }
    }
}

private const val DRAG_RESISTANCE = 0.35f
