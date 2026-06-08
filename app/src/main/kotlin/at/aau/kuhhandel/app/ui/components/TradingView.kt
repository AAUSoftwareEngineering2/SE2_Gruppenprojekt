package at.aau.kuhhandel.app.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.EaseInCubic
import androidx.compose.animation.core.EaseOutCubic
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideIn
import androidx.compose.animation.slideOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import at.aau.kuhhandel.app.R
import kotlinx.coroutines.delay
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

private const val DEFAULT_TABLE_SCALE = 3.4f
private const val DEFAULT_ANIMATION_DURATION_MS = 2000
private const val TABLE_TRAVEL_ANGLE_DEGREES = 150.0
private val DEFAULT_TABLE_OFFSET_X = 220.dp
private val DEFAULT_TABLE_OFFSET_Y = (250).dp
private val DEFAULT_TABLE_TRAVEL_DISTANCE = 2100.dp

/**
 * Full-screen trading overlay. The exposed transform values are intended to be
 * adjusted as the remaining trading UI is added around the table.
 */
@Composable
fun TradingView(
    visible: Boolean,
    modifier: Modifier = Modifier,
    tableScale: Float = DEFAULT_TABLE_SCALE,
    tableOffsetX: Dp = DEFAULT_TABLE_OFFSET_X,
    tableOffsetY: Dp = DEFAULT_TABLE_OFFSET_Y,
    travelDistance: Dp = DEFAULT_TABLE_TRAVEL_DISTANCE,
    animationDurationMillis: Int = DEFAULT_ANIMATION_DURATION_MS,
    onBackgroundClick: () -> Unit = {},
    content: @Composable BoxScope.() -> Unit = {},
) {
    val visibilityState =
        remember {
            MutableTransitionState(false).apply {
                targetState = visible
            }
        }
    visibilityState.targetState = visible

    val travelDistancePx = with(LocalDensity.current) { travelDistance.roundToPx() }
    val angleRadians = Math.toRadians(TABLE_TRAVEL_ANGLE_DEGREES)
    val entryOffset =
        IntOffset(
            x = (cos(angleRadians) * travelDistancePx).roundToInt(),
            y = (-sin(angleRadians) * travelDistancePx).roundToInt(),
        )
    val exitOffset = IntOffset(x = -entryOffset.x, y = -entryOffset.y)
    val blocksInput = visibilityState.currentState || visibilityState.targetState
    val backgroundInteractionSource = remember { MutableInteractionSource() }

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        if (blocksInput) {
            Box(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .clickable(
                            interactionSource = backgroundInteractionSource,
                            indication = null,
                            onClick = onBackgroundClick,
                        ),
            )
        }

        AnimatedVisibility(
            visibleState = visibilityState,
            enter =
                slideIn(
                    animationSpec =
                        tween(
                            durationMillis = animationDurationMillis,
                            easing = EaseOutCubic,
                        ),
                    initialOffset = { entryOffset },
                ),
            exit =
                slideOut(
                    animationSpec =
                        tween(
                            durationMillis = animationDurationMillis,
                            easing = EaseInCubic,
                        ),
                    targetOffset = { exitOffset },
                ),
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                Image(
                    painter = painterResource(R.drawable.ig_table),
                    contentDescription = null,
                    modifier =
                        Modifier
                            .align(Alignment.Center)
                            .size(width = 520.dp, height = 469.dp)
                            .offset(x = tableOffsetX, y = tableOffsetY)
                            .scale(tableScale),
                )
                content()
            }
        }
    }
}

@Preview(
    showBackground = false,
    device = "spec:width=411dp,height=891dp",
)
@Composable
private fun TradingViewRestingPreview() {
    Box(modifier = Modifier.fillMaxSize()) {
        MainBackground()
        TradingView(
            visible = true,
            tableScale = DEFAULT_TABLE_SCALE,
            tableOffsetX = DEFAULT_TABLE_OFFSET_X,
            tableOffsetY = DEFAULT_TABLE_OFFSET_Y,
        )
    }
}

@Preview(
    name = "Trading table animation",
    showBackground = false,
    device = "spec:width=411dp,height=891dp",
)
@Composable
private fun TradingViewAnimationPreview() {
    var visible by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        while (true) {
            delay(300)
            visible = true
            delay(1200)
            visible = false
            delay(700)
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        MainBackground()
        TradingView(
            visible = visible,
            tableScale = DEFAULT_TABLE_SCALE,
            tableOffsetX = DEFAULT_TABLE_OFFSET_X,
            tableOffsetY = DEFAULT_TABLE_OFFSET_Y,
        )
    }
}
