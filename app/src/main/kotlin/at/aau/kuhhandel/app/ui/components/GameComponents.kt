package at.aau.kuhhandel.app.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import at.aau.kuhhandel.app.R

@Composable
fun OtherFarm(
    farmColor: FarmColor,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    IconButton(
        onClick = onClick,
        modifier = modifier,
    ) {
        Image(
            painter = painterResource(id = parseFarmColor(farmColor)),
            contentDescription = "OtherFarm",
            modifier = Modifier.fillMaxSize(),
        )
    }
}

fun parseFarmColor(color: FarmColor): Int =
    when (color) {
        FarmColor.BLUE -> R.drawable.ig_farm_blue
        FarmColor.YELLOW -> R.drawable.ig_farm_yellow
        FarmColor.RED -> R.drawable.ig_farm_red
        FarmColor.ORANGE -> R.drawable.ig_farm_orange
        FarmColor.PURPLE -> R.drawable.ig_farm_purple
        FarmColor.WHITE -> R.drawable.ig_farm_white
    }

enum class FarmColor {
    BLUE,
    YELLOW,
    RED,
    ORANGE,
    PURPLE,
    WHITE,
}
