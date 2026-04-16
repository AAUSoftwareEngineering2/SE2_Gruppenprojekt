package at.aau.kuhhandel.app.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

/**
 * A helper component that can be dragged around the screen.
 * It prints its final coordinates to Logcat on drag end, which is useful for
 * visual positioning of UI elements.
 */
@Composable
fun DraggableElement(
    drawableId: Int,
    initialX: Float = 0f,
    initialY: Float = 0f,
    size: Int = 100,
    label: String = "Element",
) {
    var offsetX by remember { mutableStateOf(initialX) }
    var offsetY by remember { mutableStateOf(initialY) }

    Box(
        modifier =
            Modifier
                .offset { IntOffset(offsetX.roundToInt(), offsetY.roundToInt()) }
                .size(size.dp)
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragEnd = {
                            println("DEBUG: $label position -> x: ${offsetX.roundToInt()}, y: ${offsetY.roundToInt()}")
                        },
                    ) { change, dragAmount ->
                        change.consume()
                        offsetX += dragAmount.x
                        offsetY += dragAmount.y
                    }
                },
    ) {
        Image(
            painter = painterResource(id = drawableId),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
        )
    }
}
