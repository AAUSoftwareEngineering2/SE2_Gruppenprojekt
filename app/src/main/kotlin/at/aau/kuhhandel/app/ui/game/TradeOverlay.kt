package at.aau.kuhhandel.app.ui.game

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import at.aau.kuhhandel.app.ui.components.MainBackground
import at.aau.kuhhandel.app.ui.components.MoneyCardView
import at.aau.kuhhandel.app.ui.components.MoneyHand
import at.aau.kuhhandel.app.ui.components.TableCards
import at.aau.kuhhandel.app.ui.components.TradingView
import at.aau.kuhhandel.shared.enums.AnimalType
import at.aau.kuhhandel.shared.enums.GamePhase
import at.aau.kuhhandel.shared.model.GameState
import at.aau.kuhhandel.shared.model.MoneyCard
import at.aau.kuhhandel.shared.model.TradeState
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
private const val TRADE_RESULT_CARDS_SCALE = 0.6f
private const val TRADE_RESULT_REVEAL_DELAY_MS = 1_000L
private const val TRADE_RESULT_TOTAL_DELAY_MS = 3_000L

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
    TradingView(
        visible = uiState.isTradeActive,
        modifier = modifier,
        onBackgroundClick = actions.collapseHand,
    ) {
        TradeTableCards(uiState = uiState)
        TradeOverlayControls(
            uiState = uiState,
            actions = actions,
            onToggleMoneyCard = onToggleMoneyCard,
        )
    }
}

@Composable
private fun BoxScope.TradeTableCards(uiState: GameUiState) {
    if (uiState.currentPhase == GamePhase.TRADE_RESULT) {
        var showResult by
            remember(uiState.gameState?.tradeState) {
                mutableStateOf(false)
            }

        LaunchedEffect(uiState.gameState?.tradeState) {
            delay(TRADE_RESULT_REVEAL_DELAY_MS)
            showResult = true
        }

        if (showResult) {
            TradeResultCards(uiState = uiState)
        } else {
            HiddenTradeTableCards(uiState = uiState)
        }
        return
    }

    HiddenTradeTableCards(uiState = uiState)
}

@Composable
private fun BoxScope.HiddenTradeTableCards(uiState: GameUiState) {
    uiState.tradeOfferCardCount?.let { count ->
        TableCards(
            count = count,
            modifier =
                Modifier
                    .align(Alignment.Center)
                    .scale(TRADE_CARDS_SCALE)
                    .offset(
                        x = TRADE_OFFER_CARDS_OFFSET_X,
                        y = TRADE_CARDS_OFFSET_Y,
                    ),
        )
    }

    uiState.tradeCounterOfferCardCount?.let { count ->
        TableCards(
            count = count,
            isCounterOffer = true,
            modifier =
                Modifier
                    .align(Alignment.Center)
                    .scale(TRADE_CARDS_SCALE)
                    .offset(
                        x = TRADE_COUNTER_CARDS_OFFSET_X,
                        y = TRADE_COUNTER_CARDS_OFFSET_Y,
                    ),
        )
    }
}

@Composable
private fun BoxScope.TradeResultCards(uiState: GameUiState) {
    var showTotals by remember(uiState.gameState?.tradeState) { mutableStateOf(false) }

    LaunchedEffect(uiState.gameState?.tradeState) {
        delay(TRADE_RESULT_TOTAL_DELAY_MS)
        showTotals = true
    }

    TradeResultOffer(
        cards = uiState.tradeResultOfferCards,
        total = uiState.tradeResultOfferTotal,
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
        cards = uiState.tradeResultCounterOfferCards,
        total = uiState.tradeResultCounterOfferTotal,
        showTotal = showTotals,
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
        Text(
            text = total.toString(),
            style = MaterialTheme.typography.displayLarge,
            modifier = Modifier.alpha(if (showTotal) 1f else 0f),
        )
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
                .padding(bottom = 28.dp),
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
            enabled =
                uiState.selectedMoneyCardIds.isNotEmpty() &&
                    !uiState.isTradeActionSubmitting,
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
    )
}

@Composable
private fun TradeOverlayPreview(
    phase: GamePhase,
    myPlayerId: String,
    isTradeHandFanned: Boolean = false,
    isCounterOfferSelected: Boolean = false,
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
            gameState =
                GameState(
                    phase = phase,
                    tradeState =
                        TradeState(
                            initiatorId = "initiator",
                            targetId = "target",
                            requestedAnimalType = AnimalType.COW,
                            offeredMoneyCards =
                                listOf(0, 10, 50, 100)
                                    .mapIndexed { index, value ->
                                        MoneyCard("offer-$index", value)
                                    }.toSet(),
                            counterOfferedMoneyCards =
                                if (phase == GamePhase.TRADE_RESULT) {
                                    listOf(0, 10, 10, 50, 100, 200)
                                        .mapIndexed { index, value ->
                                            MoneyCard("counter-$index", value)
                                        }.toSet()
                                } else {
                                    null
                                },
                        ),
                ),
            myPlayerId = myPlayerId,
            currentPhase = phase,
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
