package at.aau.kuhhandel.app.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import at.aau.kuhhandel.app.R

private const val DEFAULT_TABLE_SCALE = 3.4f
private val DEFAULT_TABLE_OFFSET_X = 220.dp
private val DEFAULT_TABLE_OFFSET_Y = (250).dp

/**
 * Full-screen trading overlay. The exposed transform values are intended to be
 * adjusted as the remaining trading UI is added around the table.
 */
@Composable
fun TradingView(
    modifier: Modifier = Modifier,
    tableScale: Float = DEFAULT_TABLE_SCALE,
    tableOffsetX: Dp = DEFAULT_TABLE_OFFSET_X,
    tableOffsetY: Dp = DEFAULT_TABLE_OFFSET_Y,
) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            painter = painterResource(R.drawable.ig_table),
            contentDescription = null,
            modifier =
                Modifier
                    .size(width = 520.dp, height = 469.dp)
                    .offset(x = tableOffsetX, y = tableOffsetY)
                    .scale(tableScale),
        )
    }
}

@Preview(
    showBackground = false,
    device = "spec:width=411dp,height=891dp",
)
@Composable
private fun TradingViewPreview() {
    Box(modifier = Modifier.fillMaxSize()) {
        MainBackground()
        TradingView(
            tableScale = DEFAULT_TABLE_SCALE,
            tableOffsetX = DEFAULT_TABLE_OFFSET_X,
            tableOffsetY = DEFAULT_TABLE_OFFSET_Y,
        )
    }
}
