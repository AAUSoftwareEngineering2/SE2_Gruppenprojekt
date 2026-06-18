package at.aau.kuhhandel.app.ui.game

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.EaseInCubic
import androidx.compose.animation.core.EaseOutCubic
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import at.aau.kuhhandel.app.ui.components.AnimalStyle
import at.aau.kuhhandel.app.ui.components.MainBackground
import at.aau.kuhhandel.app.ui.components.MoneyCardView
import at.aau.kuhhandel.app.ui.components.MoneyHand
import at.aau.kuhhandel.app.ui.components.TableCards
import at.aau.kuhhandel.app.ui.components.TradingView
import at.aau.kuhhandel.app.ui.components.getAnimalDrawable
import at.aau.kuhhandel.app.ui.theme.DarkPurple
import at.aau.kuhhandel.app.ui.theme.PureWhite
import at.aau.kuhhandel.shared.enums.AnimalType
import at.aau.kuhhandel.shared.enums.GamePhase
import at.aau.kuhhandel.shared.model.AnimalCard
import at.aau.kuhhandel.shared.model.MoneyCard
import at.aau.kuhhandel.shared.model.Opponent
import at.aau.kuhhandel.shared.model.Player
import at.aau.kuhhandel.shared.model.TradeStateView
import kotlinx.coroutines.delay

private val TRADE_OFFER_CARDS_OFFSET_X = (70).dp
private val TRADE_COUNTER_CARDS_OFFSET_X = (-30).dp
private val TRADE_CARDS_OFFSET_Y = 5.dp
private val TRADE_COUNTER_CARDS_OFFSET_Y = (-70).dp
private val TRADE_CARDS_SCALE = 1.2f
private val TRADE_RESULT_OFFER_OFFSET_X = 85.dp
private val TRADE_RESULT_OFFER_OFFSET_Y = 75.dp
private val TRADE_RESULT_COUNTER_OFFSET_X = (-45).dp
private val TRADE_RESULT_COUNTER_OFFSET_Y = (-235).dp
private val TRADE_CARD_TRAVEL_X = 1819.dp
private val TRADE_CARD_TRAVEL_Y = 1050.dp
private val TRADE_ANIMAL_HEADSHOT_BASE_SIZE = 34.dp
private val TRADE_ANIMAL_HEADSHOT_OFFSET_X = 105.dp
private val TRADE_ANIMAL_HEADSHOT_OFFSET_Y = (-290).dp
private const val TRADE_ANIMAL_HEADSHOT_SCALE = 4f
private const val TRADE_RESULT_CARDS_SCALE = 0.6f
private const val TRADE_CARD_TRAVEL_DURATION_MS = 2_000
private const val TRADE_RESULT_GRID_DURATION_MS = 1_000L
private const val TRADE_RESULT_TOTAL_DURATION_MS = 5_000L
private const val TRADE_RESULT_COUNT_UP_DURATION_MS = 500
private const val TRADE_RESULT_TOTAL_POP_DURATION_MS = 100
private const val TRADE_RESULT_TOTAL_SETTLE_DURATION_MS = 120
private const val TRADE_RESULT_TOTAL_POP_SCALE = 1.1f
private const val TRADE_EXIT_RETENTION_MS = 2_000L

private enum class TradeResultStage {
    STACKS,
    GRIDS,
    TOTALS,
}

private enum class TradeCardDirection {
    TOP_LEFT,
    BOTTOM_RIGHT,
}

private data class TradeCardPresentation(
    val offerCount: Int?,
    val counterOfferCount: Int?,
    val offerCards: List<MoneyCard>,
    val counterOfferCards: List<MoneyCard>,
    val offerTotal: Int,
    val counterOfferTotal: Int,
    val animalType: AnimalType,
    val animalCount: Int,
    val initiatorId: String,
    val targetId: String,
    val winnerId: String?,
    val initiatorName: String,
    val targetName: String,
)

private fun GameUiState.tradeCardPresentation(): TradeCardPresentation? {
    val tradeState = tradeState ?: return null
    val animalType =
        tradeState.animalCards.firstOrNull()?.type
            ?: tradeState.requestedAnimalType
            ?: return null

    return TradeCardPresentation(
        offerCount = tradeOfferCardCount,
        counterOfferCount = tradeCounterOfferCardCount,
        offerCards = tradeResultOfferCards,
        counterOfferCards = tradeResultCounterOfferCards,
        offerTotal = tradeResultOfferTotal,
        counterOfferTotal = tradeResultCounterOfferTotal,
        animalType = animalType,
        animalCount = tradeState.animalCards.size,
        initiatorId = tradeState.initiatorId,
        targetId = tradeState.targetId,
        winnerId = tradeState.winnerId,
        initiatorName = playerName(tradeState.initiatorId),
        targetName = playerName(tradeState.targetId),
    )
}

data class TradeActions(
    val selectTargetPlayer: (String?) -> Unit,
    val selectAnimal: (AnimalType) -> Unit,
    val submitOffer: () -> Unit,
    val chooseCounterOffer: () -> Unit,
    val takeOffer: () -> Unit,
    val submitCounterOffer: () -> Unit,
    val toggleHandFanned: () -> Unit,
    val collapseHand: () -> Unit,
)

@Composable
fun TradeOverlay(
    uiState: GameUiState,
    actions: TradeActions,
    onToggleMoneyCard: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val currentPresentation = uiState.tradeCardPresentation()
    var retainedPresentation by remember { mutableStateOf(currentPresentation) }
    var resultStage by remember { mutableStateOf(TradeResultStage.STACKS) }

    LaunchedEffect(currentPresentation) {
        if (currentPresentation != null) {
            retainedPresentation = currentPresentation
        }
    }
    LaunchedEffect(uiState.isTradeActive) {
        if (!uiState.isTradeActive) {
            delay(TRADE_EXIT_RETENTION_MS)
            retainedPresentation = null
        }
    }
    LaunchedEffect(uiState.currentPhase, uiState.tradeState) {
        if (uiState.currentPhase == GamePhase.TRADE_RESULT) {
            resultStage = TradeResultStage.STACKS
            delay(TRADE_CARD_TRAVEL_DURATION_MS.toLong())
            resultStage = TradeResultStage.GRIDS
            delay(TRADE_RESULT_GRID_DURATION_MS)
            resultStage = TradeResultStage.TOTALS
            delay(TRADE_RESULT_TOTAL_DURATION_MS)
            resultStage = TradeResultStage.STACKS
        }
    }

    val presentation =
        currentPresentation
            ?: retainedPresentation.takeIf { !uiState.isTradeActive }

    Box(modifier = modifier.fillMaxSize()) {
        TradingView(
            visible = uiState.isTradeActive,
            onBackgroundClick = actions.collapseHand,
        ) {
            if (uiState.currentPhase == GamePhase.TRADE_RESULT &&
                resultStage != TradeResultStage.STACKS
            ) {
                TradeResultCards(
                    presentation = currentPresentation,
                    showTotals = resultStage == TradeResultStage.TOTALS,
                )
            }
            TradeOverlayControls(
                uiState = uiState,
                actions = actions,
                onToggleMoneyCard = onToggleMoneyCard,
            )
        }

        TradeTableCards(
            presentation = presentation,
            isTradeActive = uiState.isTradeActive,
            showStacks =
                uiState.currentPhase != GamePhase.TRADE_RESULT ||
                    resultStage == TradeResultStage.STACKS ||
                    !uiState.isTradeActive,
        )
        TradeParticipantLabels(
            presentation = currentPresentation.takeIf { uiState.isTradeActive },
        )
        TradeAnimalHeadshots(
            presentation = presentation,
            isTradeActive = uiState.isTradeActive,
        )
    }
}

@Composable
private fun BoxScope.TradeParticipantLabels(presentation: TradeCardPresentation?) {
    presentation ?: return

    TradeParticipantLabel(
        name = presentation.targetName,
        modifier =
            Modifier
                .align(Alignment.TopCenter)
                .padding(top = 24.dp),
    )
    TradeParticipantLabel(
        name = presentation.initiatorName,
        modifier =
            Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 16.dp),
    )
}

@Composable
private fun TradeParticipantLabel(
    name: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = name,
        color = PureWhite,
        style = MaterialTheme.typography.titleLarge,
        modifier =
            modifier
                .background(
                    color = DarkPurple.copy(alpha = 0.9f),
                    shape = RoundedCornerShape(16.dp),
                ).padding(horizontal = 16.dp, vertical = 6.dp),
    )
}

@Composable
private fun BoxScope.TradeAnimalHeadshots(
    presentation: TradeCardPresentation?,
    isTradeActive: Boolean,
) {
    presentation ?: return
    if (presentation.animalCount <= 0) return

    val exitDirection =
        when (presentation.winnerId) {
            presentation.targetId -> TradeCardDirection.TOP_LEFT
            presentation.initiatorId -> TradeCardDirection.BOTTOM_RIGHT
            else -> TradeCardDirection.BOTTOM_RIGHT
        }
    val exitX =
        if (exitDirection == TradeCardDirection.BOTTOM_RIGHT) {
            TRADE_CARD_TRAVEL_X
        } else {
            -TRADE_CARD_TRAVEL_X
        }
    val exitY =
        if (exitDirection == TradeCardDirection.BOTTOM_RIGHT) {
            TRADE_CARD_TRAVEL_Y
        } else {
            -TRADE_CARD_TRAVEL_Y
        }
    val targetX = if (isTradeActive) 0.dp else exitX
    val targetY = if (isTradeActive) 0.dp else exitY
    val easing = if (isTradeActive) EaseOutCubic else EaseInCubic
    val animatedX by
        animateDpAsState(
            targetValue = targetX,
            animationSpec = tween(TRADE_CARD_TRAVEL_DURATION_MS, easing = easing),
            label = "tradeAnimalX",
        )
    val animatedY by
        animateDpAsState(
            targetValue = targetY,
            animationSpec = tween(TRADE_CARD_TRAVEL_DURATION_MS, easing = easing),
            label = "tradeAnimalY",
        )

    TradeAnimalHeadshotCluster(
        animalType = presentation.animalType,
        count = presentation.animalCount,
        modifier =
            Modifier
                .align(Alignment.Center)
                .offset(
                    x = TRADE_ANIMAL_HEADSHOT_OFFSET_X + animatedX,
                    y = TRADE_ANIMAL_HEADSHOT_OFFSET_Y + animatedY,
                ),
    )
}

private data class TradeAnimalHeadshotPosition(
    val x: Dp,
    val y: Dp,
    val layer: Float,
)

@Composable
private fun TradeAnimalHeadshotCluster(
    animalType: AnimalType,
    count: Int,
    modifier: Modifier = Modifier,
) {
    val headshotSize =
        (TRADE_ANIMAL_HEADSHOT_BASE_SIZE.value * TRADE_ANIMAL_HEADSHOT_SCALE).dp
    val overlapOffset = (headshotSize.value * 0.3f).dp
    val pairOffsetX = (headshotSize.value * 0.45f).dp
    val pairOffsetY = overlapOffset
    val isFourAnimalTrade = count >= 4
    val positions =
        if (isFourAnimalTrade) {
            listOf(
                TradeAnimalHeadshotPosition(
                    x = overlapOffset,
                    y = pairOffsetY + overlapOffset,
                    layer = 0f,
                ),
                TradeAnimalHeadshotPosition(
                    x = pairOffsetX + overlapOffset,
                    y = overlapOffset,
                    layer = 0f,
                ),
                TradeAnimalHeadshotPosition(
                    x = 0.dp,
                    y = pairOffsetY,
                    layer = 1f,
                ),
                TradeAnimalHeadshotPosition(
                    x = pairOffsetX,
                    y = 0.dp,
                    layer = 1f,
                ),
            )
        } else {
            listOf(
                TradeAnimalHeadshotPosition(
                    x = overlapOffset,
                    y = overlapOffset,
                    layer = 0f,
                ),
                TradeAnimalHeadshotPosition(
                    x = 0.dp,
                    y = 0.dp,
                    layer = 1f,
                ),
            ).take(count)
        }
    val clusterWidth =
        headshotSize + overlapOffset + if (isFourAnimalTrade) pairOffsetX else 0.dp
    val clusterHeight =
        headshotSize + overlapOffset + if (isFourAnimalTrade) pairOffsetY else 0.dp

    Box(
        modifier = modifier.size(width = clusterWidth, height = clusterHeight),
    ) {
        positions.forEachIndexed { index, position ->
            Image(
                painter =
                    painterResource(
                        id = getAnimalDrawable(animalType, AnimalStyle.CHIP),
                    ),
                contentDescription = "${animalType.name} trade animal ${index + 1}",
                modifier =
                    Modifier
                        .offset(x = position.x, y = position.y)
                        .size(headshotSize)
                        .zIndex(position.layer),
            )
        }
    }
}

@Composable
private fun BoxScope.TradeTableCards(
    presentation: TradeCardPresentation?,
    isTradeActive: Boolean,
    showStacks: Boolean,
) {
    presentation ?: return

    TravelingTableCards(
        count = presentation.offerCount,
        isCounterOffer = false,
        isTradeActive = isTradeActive,
        showStack = showStacks,
        entryDirection = TradeCardDirection.BOTTOM_RIGHT,
        exitDirection = TradeCardDirection.TOP_LEFT,
        restingOffsetX = TRADE_OFFER_CARDS_OFFSET_X,
        restingOffsetY = TRADE_CARDS_OFFSET_Y,
    )
    TravelingTableCards(
        count = presentation.counterOfferCount,
        isCounterOffer = true,
        isTradeActive = isTradeActive,
        showStack = showStacks,
        entryDirection = TradeCardDirection.TOP_LEFT,
        exitDirection = TradeCardDirection.BOTTOM_RIGHT,
        restingOffsetX = TRADE_COUNTER_CARDS_OFFSET_X,
        restingOffsetY = TRADE_COUNTER_CARDS_OFFSET_Y,
    )
}

@Composable
private fun BoxScope.TravelingTableCards(
    count: Int?,
    isCounterOffer: Boolean,
    isTradeActive: Boolean,
    showStack: Boolean,
    entryDirection: TradeCardDirection,
    exitDirection: TradeCardDirection,
    restingOffsetX: Dp,
    restingOffsetY: Dp,
) {
    if (count == null || count <= 0) return

    var hasArrived by remember(count, entryDirection) { mutableStateOf(false) }
    LaunchedEffect(count, entryDirection) {
        hasArrived = true
    }

    val entryX =
        if (entryDirection == TradeCardDirection.BOTTOM_RIGHT) {
            TRADE_CARD_TRAVEL_X
        } else {
            -TRADE_CARD_TRAVEL_X
        }
    val entryY =
        if (entryDirection == TradeCardDirection.BOTTOM_RIGHT) {
            TRADE_CARD_TRAVEL_Y
        } else {
            -TRADE_CARD_TRAVEL_Y
        }
    val exitX =
        if (exitDirection == TradeCardDirection.BOTTOM_RIGHT) {
            TRADE_CARD_TRAVEL_X
        } else {
            -TRADE_CARD_TRAVEL_X
        }
    val exitY =
        if (exitDirection == TradeCardDirection.BOTTOM_RIGHT) {
            TRADE_CARD_TRAVEL_Y
        } else {
            -TRADE_CARD_TRAVEL_Y
        }
    val targetX =
        when {
            !isTradeActive -> exitX
            hasArrived -> 0.dp
            else -> entryX
        }
    val targetY =
        when {
            !isTradeActive -> exitY
            hasArrived -> 0.dp
            else -> entryY
        }
    val easing = if (isTradeActive) EaseOutCubic else EaseInCubic
    val animatedX by
        animateDpAsState(
            targetValue = targetX,
            animationSpec = tween(TRADE_CARD_TRAVEL_DURATION_MS, easing = easing),
            label = "tradeCardX",
        )
    val animatedY by
        animateDpAsState(
            targetValue = targetY,
            animationSpec = tween(TRADE_CARD_TRAVEL_DURATION_MS, easing = easing),
            label = "tradeCardY",
        )

    TableCards(
        count = count,
        isCounterOffer = isCounterOffer,
        modifier =
            Modifier
                .align(Alignment.Center)
                .scale(TRADE_CARDS_SCALE)
                .offset(
                    x = restingOffsetX + animatedX,
                    y = restingOffsetY + animatedY,
                ).alpha(if (showStack) 1f else 0f),
    )
}

@Composable
private fun BoxScope.TradeResultCards(
    presentation: TradeCardPresentation?,
    showTotals: Boolean,
) {
    presentation ?: return

    TradeResultOffer(
        cards = presentation.offerCards,
        total = presentation.offerTotal,
        showTotal = showTotals,
        modifier =
            Modifier
                .align(Alignment.Center)
                .scale(TRADE_RESULT_CARDS_SCALE)
                .offset(
                    x = TRADE_RESULT_OFFER_OFFSET_X,
                    y = TRADE_RESULT_OFFER_OFFSET_Y,
                ),
    )
    TradeResultOffer(
        cards = presentation.counterOfferCards,
        total = presentation.counterOfferTotal,
        showTotal = showTotals && presentation.counterOfferCards.isNotEmpty(),
        modifier =
            Modifier
                .align(Alignment.Center)
                .scale(TRADE_RESULT_CARDS_SCALE)
                .offset(
                    x = TRADE_RESULT_COUNTER_OFFSET_X,
                    y = TRADE_RESULT_COUNTER_OFFSET_Y,
                ),
    )
}

@Composable
private fun TradeResultOffer(
    cards: List<MoneyCard>,
    total: Int,
    showTotal: Boolean,
    modifier: Modifier = Modifier,
) {
    val displayedTotal by
        animateIntAsState(
            targetValue = if (showTotal) total else 0,
            animationSpec =
                tween(
                    durationMillis = TRADE_RESULT_COUNT_UP_DURATION_MS,
                    easing = FastOutSlowInEasing,
                ),
            label = "tradeResultTotal",
        )
    val totalScale = remember { Animatable(1f) }

    LaunchedEffect(showTotal, total) {
        totalScale.snapTo(1f)
        if (showTotal) {
            delay(TRADE_RESULT_COUNT_UP_DURATION_MS.toLong())
            totalScale.animateTo(
                targetValue = TRADE_RESULT_TOTAL_POP_SCALE,
                animationSpec = tween(TRADE_RESULT_TOTAL_POP_DURATION_MS),
            )
            totalScale.animateTo(
                targetValue = 1f,
                animationSpec = tween(TRADE_RESULT_TOTAL_SETTLE_DURATION_MS),
            )
        }
    }

    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            cards.chunked(4).forEach { rowCards ->
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    rowCards.forEach { card ->
                        MoneyCardView(
                            card = card,
                            isSelected = false,
                            onClick = {},
                            isClickable = false,
                        )
                    }
                }
            }
        }
        Box(
            modifier =
                Modifier
                    .alpha(if (showTotal) 1f else 0f)
                    .scale(totalScale.value)
                    .background(
                        color = DarkPurple.copy(alpha = 0.9f),
                        shape = RoundedCornerShape(16.dp),
                    ).padding(horizontal = 20.dp, vertical = 8.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = displayedTotal.toString(),
                color = PureWhite,
                style = MaterialTheme.typography.displayLarge,
            )
        }
    }
}

@Composable
private fun BoxScope.TradeOverlayControls(
    uiState: GameUiState,
    actions: TradeActions,
    onToggleMoneyCard: (String) -> Unit,
) {
    when {
        uiState.showsTradeOfferHand -> {
            TradeMoneySelection(
                buttonLabel = "Send Offer",
                uiState = uiState,
                onToggleMoneyCard = onToggleMoneyCard,
                onToggleTradeHandFanned = actions.toggleHandFanned,
                onConfirm = actions.submitOffer,
            )
        }

        uiState.showsTradeResponseDecision -> {
            Column(
                modifier =
                    Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 72.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = "Choose your response",
                    style = MaterialTheme.typography.titleMedium,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(
                        onClick = actions.takeOffer,
                        enabled = !uiState.isTradeActionSubmitting,
                    ) {
                        Text("Take Offer")
                    }
                    Button(
                        onClick = actions.chooseCounterOffer,
                        enabled = !uiState.isTradeActionSubmitting,
                    ) {
                        Text("Counter")
                    }
                }
            }
        }

        uiState.showsTradeCounterHand -> {
            TradeMoneySelection(
                buttonLabel = "Send Counter",
                uiState = uiState,
                onToggleMoneyCard = onToggleMoneyCard,
                onToggleTradeHandFanned = actions.toggleHandFanned,
                onConfirm = actions.submitCounterOffer,
            )
        }
    }
}

@Composable
private fun BoxScope.TradeMoneySelection(
    buttonLabel: String,
    uiState: GameUiState,
    onToggleMoneyCard: (String) -> Unit,
    onToggleTradeHandFanned: () -> Unit,
    onConfirm: () -> Unit,
) {
    Column(
        modifier =
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(bottom = 72.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = "Select",
            style = MaterialTheme.typography.headlineLarge,
        )
        MoneyHand(
            cards = uiState.myMoneyCards,
            selectedCardIds = uiState.selectedMoneyCardIds,
            onCardClick = { onToggleMoneyCard(it.id) },
            isFanned = uiState.isTradeHandFanned,
            onToggleFanned = onToggleTradeHandFanned,
            isTradePhase = true,
        )
        Button(
            onClick = onConfirm,
            enabled = !uiState.isTradeActionSubmitting,
        ) {
            Text("$buttonLabel (${uiState.selectedMoneyCardIds.size})")
        }
    }
}

@Preview(
    name = "Trade offer",
    showBackground = false,
    device = "spec:width=411dp,height=891dp",
)
@Composable
private fun TradeOfferPreview() {
    TradeOverlayPreview(
        phase = GamePhase.TRADE_OFFER,
        myPlayerId = "initiator",
        isTradeHandFanned = true,
    )
}

@Preview(
    name = "Trade response choice",
    showBackground = false,
    device = "spec:width=411dp,height=891dp",
)
@Composable
private fun TradeResponseChoicePreview() {
    TradeOverlayPreview(
        phase = GamePhase.TRADE_RESPONSE,
        myPlayerId = "target",
    )
}

@Preview(
    name = "Trade counter-offer",
    showBackground = false,
    device = "spec:width=411dp,height=891dp",
)
@Composable
private fun TradeCounterOfferPreview() {
    TradeOverlayPreview(
        phase = GamePhase.TRADE_RESPONSE,
        myPlayerId = "target",
        isTradeHandFanned = true,
        isCounterOfferSelected = true,
    )
}

@Preview(
    name = "Trade result",
    showBackground = false,
    device = "spec:width=411dp,height=891dp",
)
@Composable
private fun TradeResultPreview() {
    TradeOverlayPreview(
        phase = GamePhase.TRADE_RESULT,
        myPlayerId = "initiator",
        animalCount = 4,
    )
}

@Composable
private fun TradeOverlayPreview(
    phase: GamePhase,
    myPlayerId: String,
    isTradeHandFanned: Boolean = false,
    isCounterOfferSelected: Boolean = false,
    animalCount: Int = 2,
) {
    val moneyCards =
        listOf(
            MoneyCard("m1", 0),
            MoneyCard("m2", 10),
            MoneyCard("m3", 50),
            MoneyCard("m4", 100),
            MoneyCard("m5", 200),
        )
    val previewState =
        GameUiState(
            myPlayerId = myPlayerId,
            currentPhase = phase,
            localPlayer = Player(id = "initiator", name = "Offer Player"),
            opponents =
                listOf(
                    Opponent(
                        id = "target",
                        name = "Counter Player",
                        animals = emptyList(),
                        moneyCardCount = 6,
                        isConnected = true,
                    ),
                ),
            tradeState =
                TradeStateView(
                    initiatorId = "initiator",
                    targetId = "target",
                    requestedAnimalType = AnimalType.COW,
                    animalCards =
                        (1..animalCount)
                            .map { index ->
                                AnimalCard("cow-$index", AnimalType.COW)
                            },
                    initiatorCardCount = 4,
                    targetCardCount = if (phase == GamePhase.TRADE_RESULT) 6 else null,
                    visibleInitiatorCards =
                        if (phase == GamePhase.TRADE_RESULT) {
                            listOf(0, 10, 50, 100)
                                .mapIndexed { index, value ->
                                    MoneyCard("offer-$index", value)
                                }
                        } else {
                            null
                        },
                    visibleTargetCards =
                        if (phase == GamePhase.TRADE_RESULT) {
                            listOf(0, 10, 10, 50, 100, 200)
                                .mapIndexed { index, value ->
                                    MoneyCard("counter-$index", value)
                                }
                        } else {
                            null
                        },
                    winnerId =
                        if (phase == GamePhase.TRADE_RESULT) {
                            "initiator"
                        } else {
                            null
                        },
                ),
            myMoneyCards = moneyCards,
            selectedMoneyCardIds = setOf("m3"),
            isTradeHandFanned = isTradeHandFanned,
            isCounterOfferSelected = isCounterOfferSelected,
        )

    Box(modifier = Modifier.fillMaxSize()) {
        MainBackground()
        TradeOverlay(
            uiState = previewState,
            actions =
                TradeActions(
                    selectTargetPlayer = {},
                    selectAnimal = {},
                    submitOffer = {},
                    chooseCounterOffer = {},
                    takeOffer = {},
                    submitCounterOffer = {},
                    toggleHandFanned = {},
                    collapseHand = {},
                ),
            onToggleMoneyCard = {},
        )
    }
}
