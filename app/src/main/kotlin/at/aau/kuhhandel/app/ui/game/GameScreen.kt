package at.aau.kuhhandel.app.ui.game

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import at.aau.kuhhandel.app.R
import at.aau.kuhhandel.app.sensor.ShakeDetector
import at.aau.kuhhandel.app.ui.components.MainBackground
import at.aau.kuhhandel.app.ui.components.MoneyHand
import at.aau.kuhhandel.app.ui.components.OpponentList
import at.aau.kuhhandel.app.ui.components.PlayerFarm
import at.aau.kuhhandel.app.ui.theme.PureWhite
import at.aau.kuhhandel.shared.enums.AnimalType
import at.aau.kuhhandel.shared.enums.GamePhase
import at.aau.kuhhandel.shared.model.AnimalCard
import at.aau.kuhhandel.shared.model.MoneyCard
import at.aau.kuhhandel.shared.model.Opponent
import at.aau.kuhhandel.shared.model.Player

@Composable
fun GameScreen(
    uiState: GameUiState,
    onStartGame: () -> Unit,
    onRevealCard: () -> Unit,
    onPlaceBid: (Int) -> Unit,
    onBuyBack: (Boolean) -> Unit,
    onSubmitAuctionPayment: () -> Unit,
    tradeActions: TradeActions,
    onToggleMoneyCard: (String) -> Unit,
    onToggleHandFanned: () -> Unit,
    onCollapseHand: () -> Unit,
    onFarmTapForEye: (String) -> Unit,
    onEyeIconClick: () -> Unit,
    onPhoneShake: () -> Unit,
    onCatchSpy: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val isAuctionActive = uiState.isAuctionActive
    val isTradeActive = uiState.isTradeActive
    val gameBackgroundInteractionSource = remember { MutableInteractionSource() }
    val context = LocalContext.current

    LaunchedEffect(uiState.lastEvent) {
        val event = uiState.lastEvent
        if (event != null) {
            snackbarHostState.showSnackbar(
                message = event.message,
                withDismissAction = true,
            )
        }
    }

    DisposableEffect(context, onPhoneShake) {
        val detector =
            ShakeDetector(context) {
                onPhoneShake()
            }

        detector.startListening()

        onDispose {
            detector.stopListening()
        }
    }

    MainBackground(modifier = modifier)

    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .clickable(
                    enabled = uiState.isHandFanned && !isTradeActive,
                    interactionSource = gameBackgroundInteractionSource,
                    indication = null,
                    onClick = onCollapseHand,
                ),
    ) {
        // --- DECOR ---
        Image(
            painter = painterResource(id = R.drawable.ig_short_bush),
            contentDescription = null,
            modifier =
                Modifier
                    .align(Alignment.CenterStart)
                    .padding(start = 24.dp, top = 180.dp)
                    .size(60.dp),
            alpha = 0.6f,
        )
        Image(
            painter = painterResource(id = R.drawable.ig_tall_bush),
            contentDescription = null,
            modifier =
                Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 32.dp, top = 420.dp)
                    .size(80.dp),
            alpha = 0.6f,
        )

        // --- TOP: OPPONENTS (Hidden during auctions) ---
        if (!isAuctionActive) {
            OpponentList(
                opponents = uiState.opponents,
                hasLocalMoney = uiState.myMoneyCards.isNotEmpty(),
                onTradeTargetSelected = tradeActions.selectTargetPlayer,
                currentPhase = uiState.currentPhase,
                canSelectTradeTarget = uiState.canSelectTradeTarget,
                selectedTargetPlayerId = uiState.selectedTargetPlayerId,
                enabledTradeAnimalTypes = uiState.sharedAnimalsWithSelectedPlayer.toSet(),
                onTradeAnimalClick = tradeActions.selectAnimal,
                eyeIconPlayerId = uiState.eyeIconPlayerId,
                isCurrentlySpying = uiState.isCurrentlySpying,
                hasSpiedThisTurn = uiState.alreadySpied,
                isEyeIconHighlighted = uiState.isEyeIconHighlighted,
                spiedOnOpponentIds = uiState.spiedOnOpponentIds,
                onEyeIconClick = onEyeIconClick,
                onFarmTapForEye = onFarmTapForEye,
                modifier =
                    Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 48.dp),
            )
        }

        // --- CENTER: THE BOARD ---
        if (!isAuctionActive) {
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .align(Alignment.Center),
                contentAlignment = Alignment.Center,
            ) {
                when (uiState.currentPhase) {
                    GamePhase.NOT_STARTED -> {
                        if (uiState.canStartGame) {
                            Button(onClick = onStartGame) {
                                Text("START MATCH")
                            }
                        } else {
                            val message =
                                if (uiState.hostPlayerId == uiState.myPlayerId) {
                                    "Waiting for players (min. 3)..."
                                } else {
                                    "Waiting for host to start..."
                                }
                            GameStatusText(text = message)
                        }
                    }

                    GamePhase.PLAYER_CHOICE -> {
                        ChoicePhaseContent(
                            uiState = uiState,
                            onRevealCard = onRevealCard,
                        )
                    }

                    GamePhase.TRADE_OFFER,
                    GamePhase.TRADE_RESPONSE,
                    GamePhase.TRADE_RESULT,
                    -> Unit

                    GamePhase.FINISHED -> {
                        Text(
                            "GAME OVER",
                            style = MaterialTheme.typography.headlineLarge,
                            color = PureWhite,
                        )
                    }

                    else -> Unit
                }
            }
        }

        // --- BOTTOM: PLAYER'S OWN AREA ---
        PlayerFarm(
            player = uiState.localPlayer,
            isHandFanned = uiState.isHandFanned,
            onToggleHandFanned = onToggleHandFanned,
            selectedMoneyCardIds = uiState.selectedMoneyCardIds,
            onCardClick = { onToggleMoneyCard(it.id) },
            modifier = Modifier.align(Alignment.BottomCenter),
        )

        SnackbarHost(
            hostState = snackbarHostState,
            modifier =
                Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 140.dp),
        )

        // --- TOP LAYER: PLAYER'S MONEY HAND ---
        val myPlayer = uiState.localPlayer
        if (myPlayer != null && !isTradeActive) {
            val handTranslationY by animateDpAsState(
                targetValue = if (isAuctionActive) 115.dp else 0.dp,
                animationSpec = spring(stiffness = 200f),
                label = "handTranslationYAnimation",
            )
            val handScale by animateFloatAsState(
                targetValue = if (isAuctionActive) 1.3f else 1.0f,
                animationSpec = spring(dampingRatio = 0.8f),
                label = "handScaleAnimation",
            )

            MoneyHand(
                cards = uiState.myMoneyCards,
                selectedCardIds = uiState.selectedMoneyCardIds,
                onCardClick = { onToggleMoneyCard(it.id) },
                isFanned = uiState.isHandFanned,
                onToggleFanned = onToggleHandFanned,
                isTradePhase =
                    uiState.currentPhase in
                        listOf(
                            GamePhase.TRADE_OFFER,
                            GamePhase.TRADE_RESPONSE,
                        ),
                modifier =
                    Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 80.dp)
                        .offset(y = handTranslationY)
                        .scale(handScale),
            )
        }

        // --- SPYING REVEALED CARDS PANEL ---
        if (uiState.isCurrentlySpying) {
            Box(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .clickable(enabled = false) {},
                // Intercepts accidental underlying farm clicks
                contentAlignment = Alignment.Center,
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    uiState.spyingTargetCards?.forEach { card ->
                        Image(
                            painter = painterResource(id = getMoneyCardDrawable(card.value)),
                            contentDescription = "Spied card value: ${card.value}",
                            modifier = Modifier.size(width = 70.dp, height = 110.dp),
                        )
                    }
                }
            }
        }

        // --- SPY CATCH BUTTON ---
        if (uiState.localPlayerSpiedOn && !uiState.isHandFanned && !uiState.isCurrentlySpying) {
            Box(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .padding(bottom = 240.dp),
                contentAlignment = Alignment.BottomCenter,
            ) {
                Image(
                    painter = painterResource(id = R.drawable.spy_indicator_white),
                    contentDescription = "Catch Spy Button",
                    modifier =
                        Modifier
                            .size(110.dp)
                            .clickable { onCatchSpy() },
                )
            }
        }

        // --- AUCTION OVERLAY (Modal layer with highest priority) ---
        if (isAuctionActive) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.TopCenter,
            ) {
                AuctionPhaseContent(
                    uiState = uiState,
                    onPlaceBid = onPlaceBid,
                    onBuyBack = onBuyBack,
                    onToggleMoneyCard = onToggleMoneyCard,
                    onSubmitAuctionPayment = onSubmitAuctionPayment,
                )
            }
        }

        TradeOverlay(
            uiState = uiState,
            actions = tradeActions,
            onToggleMoneyCard = onToggleMoneyCard,
        )
    }
}

@Preview(showBackground = true, device = "spec:width=411dp,height=891dp")
@Composable
fun GameScreenPreview() {
    val players =
        listOf(
            Player(
                "1",
                "Player 1",
                moneyCards =
                    List(6) {
                        MoneyCard("a$it", 10)
                    },
                animals =
                    listOf(
                        AnimalCard(
                            "a1",
                            AnimalType.COW,
                        ),
                        AnimalCard(
                            "a2",
                            AnimalType.COW,
                        ),
                    ),
            ),
            Player(
                "2",
                "Player 2",
                moneyCards =
                    List(3) {
                        MoneyCard("b$it", 10)
                    },
                animals =
                    listOf(
                        AnimalCard("b1", AnimalType.PIG),
                        AnimalCard("b2", AnimalType.PIG),
                        AnimalCard("b3", AnimalType.PIG),
                    ),
            ),
            Player(
                "3",
                "Player 3",
                moneyCards =
                    List(5) {
                        MoneyCard("c$it", 10)
                    },
                animals = listOf(AnimalCard("c1", AnimalType.CHICKEN)),
            ),
            Player(
                "4",
                "Player 4",
                moneyCards =
                    List(12) {
                        MoneyCard("d$it", 10)
                    },
                animals =
                    listOf(
                        AnimalCard("d1", AnimalType.HORSE),
                        AnimalCard("d2", AnimalType.HORSE),
                        AnimalCard("d3", AnimalType.HORSE),
                        AnimalCard("d4", AnimalType.HORSE),
                    ),
            ),
            Player(
                "5",
                "Me",
                moneyCards =
                    List(5) {
                        MoneyCard("m$it", 50)
                    },
                animals =
                    listOf(
                        AnimalCard("m1", AnimalType.DONKEY),
                        AnimalCard("m2", AnimalType.DONKEY),
                        AnimalCard("m3", AnimalType.GOAT),
                    ),
            ),
        )
    val uiState =
        GameUiState(
            myPlayerId = "5",
            currentPhase = GamePhase.PLAYER_CHOICE,
            deckCountText = "5",
            activeCardLabel = "No card revealed",
            localPlayer = players[4],
            opponents =
                players.take(4).map { player ->
                    Opponent(
                        id = player.id,
                        name = player.name,
                        animals = player.animals,
                        moneyCardCount = player.moneyCards.size,
                    )
                },
            currentPlayerId = "5",
            isConnected = true,
            canRevealCard = true,
            canStartGame = false,
            auctionTimerSeconds = null,
            errorMessage = null,
            myMoneyCards = players[4].moneyCards,
            selectedMoneyCardIds = emptySet(),
            sharedAnimalsWithSelectedPlayer = emptyList(),
            selectedTargetPlayerId = null,
            isHandFanned = false,
        )
    GameScreen(
        uiState = uiState,
        onStartGame = {},
        onRevealCard = {},
        onPlaceBid = {},
        onBuyBack = {},
        onSubmitAuctionPayment = {},
        tradeActions =
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
        onToggleHandFanned = {},
        onCollapseHand = {},
        onFarmTapForEye = {},
        onEyeIconClick = {},
        onPhoneShake = {},
        onCatchSpy = {},
    )
}

/** Maps a money card value to its corresponding graphic resource ID. */
private fun getMoneyCardDrawable(value: Int): Int =
    when (value) {
        0 -> R.drawable.ig_money_revealed_0
        10 -> R.drawable.ig_money_revealed_10
        50 -> R.drawable.ig_money_revealed_50
        100 -> R.drawable.ig_money_revealed_100
        200 -> R.drawable.ig_money_revealed_200
        500 -> R.drawable.ig_money_revealed_500
        else -> R.drawable.ig_money_revealed_0 // Fallback asset
    }
