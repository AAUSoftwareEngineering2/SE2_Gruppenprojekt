package at.aau.kuhhandel.app.ui.game

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import at.aau.kuhhandel.app.ui.components.MainBackground
import at.aau.kuhhandel.app.ui.components.MoneyHand
import at.aau.kuhhandel.app.ui.components.OpponentList
import at.aau.kuhhandel.app.ui.components.PlayerFarm
import at.aau.kuhhandel.app.ui.theme.PureWhite
import at.aau.kuhhandel.shared.enums.AnimalType
import at.aau.kuhhandel.shared.enums.GamePhase
import at.aau.kuhhandel.shared.model.AnimalCard
import at.aau.kuhhandel.shared.model.GameState
import at.aau.kuhhandel.shared.model.MoneyCard
import at.aau.kuhhandel.shared.model.PlayerState

@Composable
fun GameScreen(
    uiState: GameUiState,
    onStartGame: () -> Unit,
    onRevealCard: () -> Unit,
    onPlaceBid: (Int) -> Unit,
    onBuyBack: (Boolean) -> Unit,
    onRespondToTrade: () -> Unit,
    onFinishTradeReveal: () -> Unit,
    onInitiateTrade: (String, AnimalType) -> Unit,
    onSelectTargetPlayer: (String?) -> Unit,
    onToggleMoneyCard: (String) -> Unit,
    onToggleHandFanned: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val isAuctionActive =
        uiState.currentPhase == GamePhase.AUCTION_BIDDING ||
            uiState.currentPhase == GamePhase.AUCTION_RESOLUTION

    LaunchedEffect(uiState.gameState?.lastEvent) {
        val event = uiState.gameState?.lastEvent
        if (event != null) {
            snackbarHostState.showSnackbar(
                message = event.message,
                withDismissAction = true,
            )
        }
    }

    MainBackground(modifier = modifier)

    // --- ANIMAL SELECTION DIALOG ---
    if (uiState.selectedTargetPlayerId != null) {
        AlertDialog(
            onDismissRequest = { onSelectTargetPlayer(null) },
            title = { Text("Pick animal to trade") },
            text = {
                Column {
                    if (uiState.sharedAnimalsWithSelectedPlayer.isEmpty()) {
                        Text("No shared animals found.")
                    } else {
                        uiState.sharedAnimalsWithSelectedPlayer.forEach { animal ->
                            TextButton(
                                onClick = {
                                    onInitiateTrade(
                                        uiState.selectedTargetPlayerId,
                                        animal,
                                    )
                                },
                            ) {
                                Text(animal.name)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { onSelectTargetPlayer(null) }) { Text("Cancel") }
            },
        )
    }

    Box(
        modifier =
            Modifier
                .fillMaxSize(),
    ) {
        // --- DECOR ---
        Image(
            painter = painterResource(id = at.aau.kuhhandel.app.R.drawable.ig_short_bush),
            contentDescription = null,
            modifier =
                Modifier
                    .align(Alignment.CenterStart)
                    .padding(start = 24.dp, top = 180.dp)
                    .size(60.dp),
            alpha = 0.6f,
        )
        Image(
            painter = painterResource(id = at.aau.kuhhandel.app.R.drawable.ig_tall_bush),
            contentDescription = null,
            modifier =
                Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 32.dp, top = 420.dp)
                    .size(80.dp),
            alpha = 0.6f,
        )

        // --- TOP: OPPONENTS ---
        OpponentList(
            players = uiState.gameState?.players ?: emptyList(),
            myId = uiState.myPlayerId,
            onOpponentClick = onSelectTargetPlayer,
            modifier =
                Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 48.dp),
        )

        // --- CENTER: THE BOARD ---
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
                            if (uiState.gameState?.hostPlayerId == uiState.myPlayerId) {
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

                GamePhase.AUCTION_BIDDING,
                GamePhase.AUCTION_RESOLUTION,
                -> {
                    AuctionPhaseContent(
                        uiState = uiState,
                        onPlaceBid = onPlaceBid,
                        onBuyBack = onBuyBack,
                    )
                }

                GamePhase.TRADE_OFFER,
                GamePhase.TRADE_RESPONSE,
                GamePhase.TRADE_REVEAL,
                -> {
                    TradePhaseContent(
                        uiState = uiState,
                        onRespondToTrade = onRespondToTrade,
                        onFinishTradeReveal = onFinishTradeReveal,
                    )
                }

                GamePhase.FINISHED -> {
                    Text(
                        "GAME OVER",
                        style = MaterialTheme.typography.headlineLarge,
                        color = PureWhite,
                    )
                }
            }
        }

        // --- BOTTOM: PLAYER'S OWN AREA ---
        PlayerFarm(
            player = uiState.gameState?.players?.find { it.id == uiState.myPlayerId },
            isHandFanned = uiState.isHandFanned,
            onToggleHandFanned = onToggleHandFanned,
            selectedMoneyCardIds = uiState.selectedMoneyCardIds,
            onCardClick = { onToggleMoneyCard(it.id) },
            modifier = Modifier.align(Alignment.BottomCenter),
        )

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 140.dp),
        )

        // --- TOP LAYER: PLAYER'S MONEY HAND ---
        // Placing it last in the Box ensures it's on top of everything else
        val myPlayer = uiState.gameState?.players?.find { it.id == uiState.myPlayerId }
        if (myPlayer != null) {
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
                cards = myPlayer.moneyCards,
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
    }
}

@Preview(showBackground = true, device = "spec:width=411dp,height=891dp")
@Composable
fun GameScreenPreview() {
    val players =
        listOf(
            PlayerState(
                "1",
                "Player 1",
                moneyCards = List(6) { MoneyCard("a$it", 10) },
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
            PlayerState(
                "2",
                "Player 2",
                moneyCards = List(3) { MoneyCard("b$it", 10) },
                animals =
                    listOf(
                        AnimalCard("b1", AnimalType.PIG),
                        AnimalCard("b2", AnimalType.PIG),
                        AnimalCard("b3", AnimalType.PIG),
                    ),
            ),
            PlayerState(
                "3",
                "Player 3",
                moneyCards = List(5) { MoneyCard("c$it", 10) },
                animals = listOf(AnimalCard("c1", AnimalType.CHICKEN)),
            ),
            PlayerState(
                "4",
                "Player 4",
                moneyCards = List(12) { MoneyCard("d$it", 10) },
                animals =
                    listOf(
                        AnimalCard("d1", AnimalType.HORSE),
                        AnimalCard("d2", AnimalType.HORSE),
                        AnimalCard("d3", AnimalType.HORSE),
                        AnimalCard("d4", AnimalType.HORSE),
                    ),
            ),
            PlayerState(
                "5",
                "Me",
                moneyCards = List(5) { MoneyCard("m$it", 50) },
                animals =
                    listOf(
                        AnimalCard("m1", AnimalType.DONKEY),
                        AnimalCard("m2", AnimalType.DONKEY),
                        AnimalCard("m3", AnimalType.GOAT),
                    ),
            ),
        )
    val gameState =
        GameState(
            phase = GamePhase.PLAYER_CHOICE,
            players = players,
            currentPlayerIndex = 4,
        )
    val uiState =
        GameUiState(
            gameState = gameState,
            myPlayerId = "5",
            currentPhase = GamePhase.PLAYER_CHOICE,
            deckCountText = "5",
            canRevealCard = true,
            myMoneyCards = players[4].moneyCards,
            isHandFanned = false,
        )
    GameScreen(
        uiState = uiState,
        onStartGame = {},
        onRevealCard = {},
        onPlaceBid = {},
        onBuyBack = {},
        onRespondToTrade = {},
        onFinishTradeReveal = {},
        onInitiateTrade = { _, _ -> },
        onSelectTargetPlayer = {},
        onToggleMoneyCard = {},
        onToggleHandFanned = {},
    )
}
