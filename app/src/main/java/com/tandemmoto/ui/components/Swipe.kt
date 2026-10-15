package com.tandemmoto.ui.components

import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp

/**
 * A sideways swipe across the screen: [onSwipeLeft] when the finger moves right to left,
 * [onSwipeRight] when it moves left to right, once per swipe and only past [SWIPE_DISTANCE].
 * Children that use horizontal drags themselves (the seek bar, a row's drag handle) get them
 * first, so dragging those never counts as a swipe.
 */
fun Modifier.horizontalSwipe(
    onSwipeLeft: (() -> Unit)? = null,
    onSwipeRight: (() -> Unit)? = null
): Modifier = composed {
    val left by rememberUpdatedState(onSwipeLeft)
    val right by rememberUpdatedState(onSwipeRight)
    pointerInput(Unit) {
        val distance = SWIPE_DISTANCE.toPx()
        var dragged = 0f
        var done = false
        detectHorizontalDragGestures(
            onDragStart = {
                dragged = 0f
                done = false
            },
            onHorizontalDrag = { change, amount ->
                dragged += amount
                if (done) return@detectHorizontalDragGestures
                val action = when {
                    dragged <= -distance -> left
                    dragged >= distance -> right
                    else -> null
                }
                if (action != null) {
                    change.consume()
                    done = true
                    action()
                }
            }
        )
    }
}

private val SWIPE_DISTANCE = 80.dp
