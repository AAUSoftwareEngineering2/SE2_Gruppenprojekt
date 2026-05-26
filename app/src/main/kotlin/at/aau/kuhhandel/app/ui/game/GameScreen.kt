package at.aau.kuhhandel.app.ui.game

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import at.aau.kuhhandel.app.ui.components.AnimalStyle
import at.aau.kuhhandel.app.ui.components.AuctionControls
import at.aau.kuhhandel.app.ui.components.AuctionView
import at.aau.kuhhandel.app.ui.components.DeckView
import at.aau.kuhhandel.app.ui.components.MainBackground
import at.aau.kuhhandel.app.ui.components.MoneyHand
import at.aau.kuhhandel.app.ui.components.OpponentList
import at.aau.kuhhandel.app.ui.components.PlayerFarm
import at.aau.kuhhandel.app.ui.components.TradeView
import at.aau.kuhhandel.app.ui.components.getAnimalDrawable
import at.aau.kuhhandel.app.ui.theme.DarkPurple
import at.aau.kuhhandel.app.ui.theme.PureWhite
import at.aau.kuhhandel.shared.enums.AnimalType
import at.aau.kuhhandel.shared.enums.GamePhase
import at.aau.kuhhandel.shared.model.GameEvent
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

    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.6f,
        targetValue = 1f,
        animationSpec =
            infiniteRepeatable(
                animation = tween(800, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse,
            ),
        label = "pulseAlpha",
    )

    LaunchedEffect(uiState.gameState?.lastEvent) {
        val event = uiState.gameState?.lastEvent
        if (event is GameEvent.MoneyBonus) {
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

                        if (uiState.currentPhase == GamePhase.TRADE_REVEAL) {
                            Button(
                                onClick = onFinishTradeReveal,
                                modifier = Modifier.padding(top = 16.dp),
                            ) {
                                Text("CONTINUE")
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
        if (uiState.currentPhase !in
            listOf(GamePhase.AUCTION_BIDDING, GamePhase.AUCTION_RESOLUTION)
        ) {
            OpponentList(
                players = uiState.gameState?.players ?: emptyList(),
                myId = uiState.myPlayerId,
                onOpponentClick = onSelectTargetPlayer,
                modifier =
                    Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 48.dp),
            )
        }

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
                        Text(
                            text = message,
                            style = MaterialTheme.typography.headlineSmall,
                            color = PureWhite,
                        )
                    }
                }

                GamePhase.PLAYER_CHOICE -> {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        val revealedCard =
                            if (uiState.currentPhase == GamePhase.PLAYER_CHOICE) {
                                null // Force deck view in CHOICE phase
                            } else {
                                uiState.gameState?.currentFaceUpCard
                            }

                        revealedCard?.let { card ->
                            Image(
                                painter =
                                    painterResource(
                                        id = getAnimalDrawable(card.type, AnimalStyle.CARD),
                                    ),
                                contentDescription = null,
                                modifier = Modifier.size(width = 150.dp, height = 210.dp),
                            )
                            Text(
                                card.type.name,
                                style = MaterialTheme.typography.headlineMedium,
                                color = PureWhite,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(top = 8.dp),
                            )
                        } ?: run {
                            DeckView(
                                count = uiState.deckCountText,
                                onClick = onRevealCard,
                                canClick = uiState.isMyTurn,
                            )
                            if (!uiState.isMyTurn) {
                                Text(
                                    "Waiting for ${uiState.activePlayerName}...",
                                    style =
                                        MaterialTheme.typography.headlineSmall.copy(
                                            shadow =
                                                Shadow(
                                                    color = PureWhite,
                                                    offset = Offset(4f, 4f),
                                                    blurRadius = 8f,
                                                ),
                                        ),
                                    color = DarkPurple.copy(alpha = alpha),
                                    modifier = Modifier.padding(top = 16.dp),
                                )
                            }
                        }

                        if (uiState.currentPhase == GamePhase.TRADE_REVEAL) {
                            Button(
                                onClick = onFinishTradeReveal,
                                modifier = Modifier.padding(top = 16.dp),
                            ) {
                                Text("CONTINUE")
                            }
                        }
                    }
                }

                GamePhase.AUCTION_BIDDING,
                GamePhase.AUCTION_RESOLUTION,
                -> {
                    // During auctions, we move the UI to the top and hide opponents to avoid overlap with money cards
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.TopCenter,
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.padding(top = 24.dp),
                        ) {
                            AuctionView(
                                auction = uiState.gameState?.auctionState,
                                timerSeconds = uiState.auctionTimerSeconds,
                                players = uiState.gameState?.players ?: emptyList(),
                            )
                            if (uiState.isConnected &&
                                !uiState.isAuctioneer &&
                                (uiState.gameState?.phase == GamePhase.AUCTION_BIDDING)
                            ) {
                                AuctionControls(
                                    onBid = onPlaceBid,
                                    currentBid = uiState.gameState?.auctionState?.highestBid ?: 0,
                                    myTotalMoney = uiState.myTotalMoney,
                                )
                            } else if (uiState.isAuctioneer &&
                                (uiState.gameState?.phase != GamePhase.AUCTION_BIDDING)
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(
                                        "Auction Closed. Choose your action:",
                                        style =
                                            MaterialTheme.typography.headlineSmall.copy(
                                                shadow =
                                                    Shadow(
                                                        color = PureWhite,
                                                        offset = Offset(4f, 4f),
                                                        blurRadius = 8f,
                                                    ),
                                            ),
                                        color = DarkPurple.copy(alpha = alpha),
                                        modifier = Modifier.padding(bottom = 8.dp),
                                    )
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Button(onClick = { onBuyBack(true) }) { Text("Buy Back") }
                                        Button(
                                            onClick = { onBuyBack(false) },
                                        ) { Text("Let Winner Buy") }
                                    }
                                }
                            }

                            if (uiState.currentPhase == GamePhase.TRADE_REVEAL) {
                                Button(
                                    onClick = onFinishTradeReveal,
                                    modifier = Modifier.padding(top = 16.dp),
                                ) {
                                    Text("CONTINUE")
                                }
                            }
                        }
                    }
                }

                GamePhase.TRADE_OFFER,
                GamePhase.TRADE_RESPONSE,
                GamePhase.TRADE_REVEAL,
                -> {
                    val trade = uiState.gameState?.tradeState
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        TradeView(
                            trade = trade,
                            onAccept = { onRespondToTrade() },
                            onCounter = { onRespondToTrade() },
                            modifier = Modifier,
                            myId = uiState.myPlayerId,
                        )
                        // If I am the initiator, I might need to send my first offer
                        if (trade?.initiatorId == uiState.myPlayerId &&
                            (trade?.offeredMoneyCardIds?.size ?: 0) == 0
                        ) {
                            Button(
                                onClick = { onRespondToTrade() },
                                modifier = Modifier.padding(top = 8.dp),
                                enabled = uiState.selectedMoneyCardIds.isNotEmpty(),
                            ) {
                                Text("Send Offer (${uiState.selectedMoneyCardIds.size})")
                            }
                        }

                        if (uiState.currentPhase == GamePhase.TRADE_REVEAL) {
                            Button(
                                onClick = onFinishTradeReveal,
                                modifier = Modifier.padding(top = 16.dp),
                            ) {
                                Text("CONTINUE")
                            }
                        }
                    }
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
            isMyTurn = uiState.isMyTurn,
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
                        .padding(bottom = 80.dp), // Base position adjusted for screen layout
            )
        }
    }
}

@Preview(showBackground = true, device = "spec:width=411dp,height=891dp")
@Composable
fun GameScreenPreview() {
    val players =
        listOf(
            PlayerState("1", "Player 1", moneyCards = List(6) { MoneyCard("a$it", 10) }),
            PlayerState("2", "Player 2", moneyCards = List(3) { MoneyCard("b$it", 10) }),
            PlayerState("3", "Player 3", moneyCards = List(5) { MoneyCard("c$it", 10) }),
            PlayerState("4", "Player 4", moneyCards = List(12) { MoneyCard("d$it", 10) }),
            PlayerState("5", "Me", moneyCards = List(5) { MoneyCard("m$it", 50) }),
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
