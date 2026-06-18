package at.aau.kuhhandel.app.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import at.aau.kuhhandel.app.R
import at.aau.kuhhandel.app.ui.theme.DarkPurple
import at.aau.kuhhandel.app.ui.theme.DefaultPurple
import at.aau.kuhhandel.app.ui.theme.PureWhite
import at.aau.kuhhandel.shared.enums.AnimalType
import at.aau.kuhhandel.shared.enums.GamePhase
import at.aau.kuhhandel.shared.model.MoneyCard
import at.aau.kuhhandel.shared.model.Opponent
import at.aau.kuhhandel.shared.model.Player
import kotlin.math.cos
import kotlin.math.sin

/** Displays a summary of an opponent's farm, including their name and money card count. */
@Composable
fun OtherFarm(
    player: Opponent,
    farmColor: FarmColor,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    showName: Boolean = true,
    canClick: Boolean = true,
    showTradeAnimalPicker: Boolean = false,
    enabledTradeAnimalTypes: Set<AnimalType> = emptySet(),
    onTradeAnimalClick: (AnimalType) -> Unit = {},
    isEyeIconVisible: Boolean = false,
    onEyeIconClick: () -> Unit = {},
    isEyeIconGreyedOut: Boolean = false,
    isEyeIconHighlighted: Boolean = false,
    isSpyIndicatorVisible: Boolean = false,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier =
            modifier
                .padding(4.dp)
                .clickable(enabled = canClick) { onClick() },
    ) {
        // Hide name during auctions to match mockup layout
        // Using alpha instead of conditional visibility to prevent layout shifts
        Text(
            text = player.name,
            style =
                MaterialTheme.typography.titleMedium.copy(
                    shadow =
                        Shadow(
                            color = PureWhite,
                            offset = Offset(2f, 2f),
                            blurRadius = 4f,
                        ),
                ),
            color = DarkPurple,
            fontWeight = FontWeight.Bold,
            modifier =
                Modifier.drawWithContent {
                    if (showName) drawContent()
                },
        )
        Box(contentAlignment = Alignment.Center) {
            Image(
                painter = painterResource(id = parseFarmColor(farmColor)),
                contentDescription = "OtherFarm",
                modifier = Modifier.size(165.dp),
            )
            // Display animal chips in the middle of the farm
            Box(
                modifier =
                    Modifier
                        .size(width = 135.dp, height = 100.dp)
                        .align(Alignment.Center),
                contentAlignment = Alignment.Center,
            ) {
                AnimalFarmChipsView(
                    animals = player.animals,
                    chipSize = 34.dp,
                )
            }
            Box(
                modifier =
                    Modifier
                        .align(Alignment.BottomEnd)
                        .offset(x = (-7).dp, y = (-7).dp)
                        .size(width = 58.dp, height = 48.dp),
                contentAlignment = Alignment.Center,
            ) {
                Image(
                    painter =
                        painterResource(
                            id = getHiddenMoneyDiagonalDrawable(player.moneyCardCount),
                        ),
                    contentDescription = null,
                    modifier = Modifier.size(width = 58.dp, height = 48.dp),
                )
                Text(
                    text = player.moneyCardCount.toString(),
                    style =
                        MaterialTheme.typography.titleSmall.copy(
                            shadow =
                                Shadow(
                                    color = PureWhite,
                                    offset = Offset(0f, 0f),
                                    blurRadius = 6f,
                                ),
                        ),
                    color = DarkPurple,
                    fontWeight = FontWeight.ExtraBold,
                )
            }
            if (showTradeAnimalPicker) {
                TradeAnimalRadialPicker(
                    enabledAnimalTypes = enabledTradeAnimalTypes,
                    onAnimalClick = onTradeAnimalClick,
                )
            }
            if (isEyeIconVisible) {
                val eyeAlpha = if (isEyeIconGreyedOut) 0.4f else 1.0f
                val greyScaleFilter =
                    remember {
                        ColorFilter.colorMatrix(
                            ColorMatrix().apply { setToSaturation(0f) },
                        )
                    }

                Box(
                    modifier =
                        Modifier
                            .align(Alignment.TopStart)
                            .offset(x = 10.dp, y = 25.dp)
                            .size(44.dp)
                            .then(
                                if (!isEyeIconGreyedOut) {
                                    Modifier.clickable { onEyeIconClick() }
                                } else {
                                    Modifier
                                },
                            ),
                    contentAlignment = Alignment.Center,
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.spy_eye),
                        contentDescription = "Spy Eye Icon",
                        alpha = eyeAlpha,
                        colorFilter = if (isEyeIconGreyedOut) greyScaleFilter else null,
                        modifier =
                            Modifier
                                .fillMaxSize()
                                .then(
                                    if (isEyeIconHighlighted) {
                                        Modifier.border(
                                            width = 3.dp,
                                            color = at.aau.kuhhandel.app.ui.theme.DefaultPurple,
                                            shape = CircleShape,
                                        )
                                    } else {
                                        Modifier
                                    },
                                ),
                    )
                }
            }
            if (isSpyIndicatorVisible) {
                Image(
                    painter = painterResource(id = R.drawable.spy_indicator_white),
                    contentDescription = "Player being spied on",
                    modifier =
                        Modifier
                            .align(Alignment.TopEnd)
                            .offset(x = (-10).dp, y = 25.dp)
                            .size(36.dp),
                )
            }
        }
    }
}

/** Renders a grid of all opponents in the game. */
@Composable
fun OpponentList(
    opponents: List<Opponent>,
    hasLocalMoney: Boolean,
    onTradeTargetSelected: (String) -> Unit,
    modifier: Modifier = Modifier,
    currentPhase: GamePhase = GamePhase.NOT_STARTED,
    isAuctionActive: Boolean = false,
    canSelectTradeTarget: Boolean = true,
    selectedTargetPlayerId: String? = null,
    enabledTradeAnimalTypes: Set<AnimalType> = emptySet(),
    onTradeAnimalClick: (AnimalType) -> Unit = {},
    onFarmTapForEye: (String) -> Unit = {},
    eyeIconPlayerId: String? = null,
    onEyeIconClick: () -> Unit = {},
    isEyeIconHighlighted: Boolean = false,
    isCurrentlySpying: Boolean = false,
    hasSpiedThisTurn: Boolean = false,
    spiedOnOpponentIds: List<String> = emptyList(),
) {
    val isChoicePhase = currentPhase == GamePhase.PLAYER_CHOICE

    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        opponents.chunked(2).forEachIndexed { rowIndex, rowPlayers ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                rowPlayers.forEachIndexed { colIndex, player ->
                    val index = (rowIndex * 2) + colIndex
                    val color = FarmColor.entries[index % FarmColor.entries.size]

                    val isSelectedForEye = eyeIconPlayerId == player.id

                    val isGreyedOut = hasSpiedThisTurn || isCurrentlySpying || !hasLocalMoney
                    val anyoneElseSpiedOnThem = spiedOnOpponentIds.contains(player.id)

                    OtherFarm(
                        player = player,
                        farmColor = color,
                        onClick = {
                            if (isChoicePhase && !canSelectTradeTarget) {
                                onFarmTapForEye(player.id)
                            } else {
                                onTradeTargetSelected(player.id)
                            }
                        },
                        showName = !isAuctionActive,
                        canClick = canSelectTradeTarget || isChoicePhase,
                        showTradeAnimalPicker =
                            canSelectTradeTarget && selectedTargetPlayerId == player.id,
                        enabledTradeAnimalTypes = enabledTradeAnimalTypes,
                        onTradeAnimalClick = onTradeAnimalClick,
                        isEyeIconVisible = isSelectedForEye,
                        onEyeIconClick = onEyeIconClick,
                        isEyeIconGreyedOut = isGreyedOut,
                        isEyeIconHighlighted = isEyeIconHighlighted && isSelectedForEye,
                        isSpyIndicatorVisible = anyoneElseSpiedOnThem,
                    )
                }
            }
        }
    }
}

@Composable
private fun BoxScope.TradeAnimalRadialPicker(
    enabledAnimalTypes: Set<AnimalType>,
    onAnimalClick: (AnimalType) -> Unit,
) {
    val disabledColorFilter =
        remember {
            ColorFilter.colorMatrix(
                ColorMatrix().apply {
                    setToSaturation(0f)
                },
            )
        }
    val iconSize = 50.dp
    val radius = 98.dp

    AnimalType.entries.forEachIndexed { index, animalType ->
        val isEnabled = animalType in enabledAnimalTypes
        val angleRadians =
            Math.toRadians(
                -90.0 + (360.0 / AnimalType.entries.size) * index,
            )
        val xOffset = (cos(angleRadians) * radius.value).toFloat().dp
        val yOffset = (sin(angleRadians) * radius.value).toFloat().dp

        Image(
            painter = painterResource(id = getAnimalDrawable(animalType, AnimalStyle.CHIP)),
            contentDescription = "${animalType.name} trade option",
            colorFilter = if (isEnabled) null else disabledColorFilter,
            modifier =
                Modifier
                    .align(Alignment.Center)
                    .offset(x = xOffset, y = yOffset)
                    .size(iconSize)
                    .then(
                        if (isEnabled) {
                            Modifier.clickable { onAnimalClick(animalType) }
                        } else {
                            Modifier
                        },
                    ),
        )
    }
}

/** Displays the current player's farm area, including their hand of money cards and turn status. */
@Composable
fun PlayerFarm(
    modifier: Modifier = Modifier,
    player: Player?,
    isHandFanned: Boolean = false,
    onToggleHandFanned: () -> Unit = {},
    selectedMoneyCardIds: Set<String> = emptySet(),
    onCardClick: (MoneyCard) -> Unit = {},
) {
    Box(
        modifier = modifier.fillMaxWidth(),
        contentAlignment = Alignment.BottomCenter,
    ) {
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(180.dp),
            contentAlignment = Alignment.BottomCenter,
        ) {
            Image(
                painter = painterResource(id = R.drawable.ig_farm_self),
                contentDescription = "My Farm Area",
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(180.dp),
                contentScale = ContentScale.FillWidth,
                alignment = Alignment.Center,
            )

            // Display my own animal chips - centered and bigger
            if (player != null) {
                AnimalFarmChipsView(
                    animals = player.animals,
                    chipSize = 48.dp,
                    modifier =
                        Modifier
                            .align(Alignment.CenterStart)
                            .padding(start = 64.dp, bottom = 48.dp),
                )
            }

            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp, start = 24.dp, end = 24.dp),
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.Start,
            ) {
                Column {
                    Text(
                        text = player?.name ?: "YOU",
                        style =
                            MaterialTheme.typography.headlineMedium.copy(
                                shadow =
                                    Shadow(
                                        color = PureWhite,
                                        offset = Offset(4f, 4f),
                                        blurRadius = 8f,
                                    ),
                            ),
                        color = DefaultPurple,
                        fontWeight = FontWeight.Black,
                    )
                }
            }
        }
    }
}

/** Maps a [FarmColor] to its corresponding farm drawable resource ID. */
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
