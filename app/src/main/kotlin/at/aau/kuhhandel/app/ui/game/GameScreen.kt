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
import at.aau.kuhhandel.shared.model.Player

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
    val isAuctionActive = uiState.isAuctionActive

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

    Box(modifier = Modifier.fillMaxSize()) {
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

        // --- TOP: OPPONENTS (Hidden during auctions) ---
        if (!isAuctionActive) {
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

                    GamePhase.TRADE_OFFER,
                    GamePhase.TRADE_RESPONSE,
                    GamePhase.TRADE_RESULT,
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

                    else -> Unit
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
            modifier =
                Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 140.dp),
        )

        // --- TOP LAYER: PLAYER'S MONEY HAND ---
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
                )
            }
        }
    }
}


