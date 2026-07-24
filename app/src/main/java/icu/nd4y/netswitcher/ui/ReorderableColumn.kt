package icu.nd4y.netswitcher.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import kotlin.math.roundToInt

/**
 * A vertical list whose rows can be dragged after a long press.
 *
 * The underlying list is deliberately left alone until the finger lifts: reordering
 * mid-gesture would shift indices under the active pointer handler and cancel the
 * drag. Instead the dragged row follows the finger, its neighbours slide out of the
 * way, and a single [onMove] is emitted at the end.
 *
 * Rows are assumed to be the same height, which holds for the uniform cards it draws.
 */
@Composable
fun ReorderableColumn(
    count: Int,
    onMove: (from: Int, to: Int) -> Unit,
    modifier: Modifier = Modifier,
    spacing: Dp = 10.dp,
    row: @Composable (index: Int, isDragging: Boolean) -> Unit,
) {
    var dragIndex by remember { mutableIntStateOf(-1) }
    var dragOffset by remember { mutableFloatStateOf(0f) }
    var rowHeight by remember { mutableIntStateOf(0) }

    val spacingPx = with(LocalDensity.current) { spacing.toPx() }
    val step = rowHeight + spacingPx
    val onDragStart = rememberDragHaptics()

    val targetIndex =
        if (dragIndex < 0 || step <= 0f) -1
        else (dragIndex + (dragOffset / step).roundToInt()).coerceIn(0, count - 1)

    Column(modifier.fillMaxWidth()) {
        repeat(count) { index ->
            if (index > 0) Spacer(Modifier.height(spacing))

            val isDragging = index == dragIndex
            // Neighbours between the source and the target make room for the card.
            val shift = when {
                dragIndex < 0 || targetIndex < 0 || isDragging -> 0f
                dragIndex < targetIndex && index in (dragIndex + 1)..targetIndex -> -step
                dragIndex > targetIndex && index in targetIndex until dragIndex -> step
                else -> 0f
            }
            val animatedShift by animateFloatAsState(shift, label = "reorder-shift")

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .onSizeChanged { if (it.height > 0) rowHeight = it.height }
                    .zIndex(if (isDragging) 1f else 0f)
                    .graphicsLayer {
                        translationY = if (isDragging) dragOffset else animatedShift
                        val scale = if (isDragging) 1.03f else 1f
                        scaleX = scale
                        scaleY = scale
                    }
                    .alpha(if (isDragging) 0.92f else 1f)
                    .pointerInput(count, index) {
                        detectDragGesturesAfterLongPress(
                            onDragStart = {
                                dragIndex = index
                                dragOffset = 0f
                                onDragStart()
                            },
                            onDrag = { change, amount ->
                                change.consume()
                                dragOffset += amount.y
                            },
                            onDragEnd = {
                                val from = dragIndex
                                val to = targetIndex
                                dragIndex = -1
                                dragOffset = 0f
                                if (from >= 0 && to >= 0 && from != to) onMove(from, to)
                            },
                            onDragCancel = {
                                dragIndex = -1
                                dragOffset = 0f
                            },
                        )
                    },
            ) {
                row(index, isDragging)
            }
        }
    }
}

/** Moves a single element, the shape [ReorderableColumn] reports its drags in. */
fun <T> List<T>.moveItem(from: Int, to: Int): List<T> {
    if (from == to || from !in indices || to !in indices) return this
    val copy = toMutableList()
    copy.add(to, copy.removeAt(from))
    return copy
}
