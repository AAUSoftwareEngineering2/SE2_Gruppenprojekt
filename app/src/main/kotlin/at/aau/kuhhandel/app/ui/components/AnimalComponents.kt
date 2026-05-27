package at.aau.kuhhandel.app.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import at.aau.kuhhandel.shared.enums.AnimalType
import at.aau.kuhhandel.shared.model.AnimalCard

/**
 * Renders a stack of animal chips.
 * A full quartet is 4 chips.
 */
@Composable
fun AnimalChipStackView(
    type: AnimalType,
    count: Int,
    modifier: Modifier = Modifier,
    chipSize: Dp = 32.dp,
    stackOffset: Dp = (chipSize.value * 0.12f).dp,
) {
    val clampedCount = count.coerceIn(0, 4)
    Box(
        modifier =
            modifier
                .size(chipSize, chipSize + (stackOffset * 3)),
        contentAlignment = Alignment.BottomCenter,
    ) {
        for (i in 0 until clampedCount) {
            Image(
                painter = painterResource(id = getAnimalDrawable(type, AnimalStyle.CHIP)),
                contentDescription = "${type.name} chip $i",
                modifier =
                    Modifier
                        .size(chipSize)
                        .offset(y = -(stackOffset * i))
                        .clip(CircleShape)
                        .border(0.5.dp, Color.Black.copy(alpha = 0.5f), CircleShape),
            )
        }
    }
}

/**
 * Renders all animal chips for a player, grouped by type.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AnimalFarmChipsView(
    animals: List<AnimalCard>,
    modifier: Modifier = Modifier,
    chipSize: Dp = 32.dp,
) {
    val animalGroups = animals.groupBy { it.type }

    FlowRow(
        modifier = modifier.wrapContentSize(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        maxItemsInEachRow = 4,
    ) {
        AnimalType.entries.forEach { type ->
            val count = animalGroups[type]?.size ?: 0
            if (count > 0) {
                AnimalChipStackView(
                    type = type,
                    count = count,
                    chipSize = chipSize,
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun AnimalChipStackViewPreview() {
    AnimalChipStackView(type = AnimalType.COW, count = 3)
}

@Preview(showBackground = true)
@Composable
fun AnimalFarmChipsViewPreview() {
    val animals =
        listOf(
            AnimalCard("1", AnimalType.COW),
            AnimalCard("2", AnimalType.COW),
            AnimalCard("3", AnimalType.COW),
            AnimalCard("4", AnimalType.PIG),
            AnimalCard("5", AnimalType.PIG),
            AnimalCard("6", AnimalType.CHICKEN),
            AnimalCard("7", AnimalType.HORSE),
            AnimalCard("8", AnimalType.HORSE),
            AnimalCard("9", AnimalType.HORSE),
            AnimalCard("10", AnimalType.HORSE),
        )
    AnimalFarmChipsView(animals = animals)
}
