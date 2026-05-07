package at.aau.kuhhandel.app.ui.game

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import at.aau.kuhhandel.app.ui.components.AuctionView
import at.aau.kuhhandel.app.ui.components.DeckView
import at.aau.kuhhandel.app.ui.components.MainBackground
import at.aau.kuhhandel.app.ui.components.OpponentList
import at.aau.kuhhandel.app.ui.components.PlayerFarm
import at.aau.kuhhandel.app.ui.components.getAnimalDrawable
import at.aau.kuhhandel.shared.enums.GamePhase
import at.aau.kuhhandel.shared.model.GameState
import at.aau.kuhhandel.shared.model.MoneyCard
import at.aau.kuhhandel.shared.model.PlayerState

@Composable
fun GameScreen(
    uiState: GameUiState,
    onStartGame: () -> Unit,
    onRevealCard: () -> Unit,
    modifier: Modifier = Modifier,
) {
    MainBackground(modifier = modifier)

    Box(
        modifier =
            Modifier
                .fillMaxSize(),
    ) {
        // --- DECOR ---
        // Left side bush
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
        // Right side bush
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
        // Bottom decor
        Image(
            painter = painterResource(id = at.aau.kuhhandel.app.R.drawable.ig_short_bush),
            contentDescription = null,
            modifier =
                Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = 40.dp, bottom = 220.dp)
                    .size(70.dp),
            alpha = 0.6f,
        )

        // --- TOP: OPPONENTS ---
        OpponentList(
            players = uiState.gameState?.players ?: emptyList(),
            myId = uiState.myPlayerId,
            modifier =
                Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 16.dp),
        )

        // --- CENTER: THE BOARD ---
        Box(
            modifier = Modifier.fillMaxWidth().align(Alignment.Center),
            contentAlignment = Alignment.Center,
        ) {
            when (uiState.currentPhase) {
                GamePhase.NOT_STARTED -> {
                    if (uiState.canStartGame) {
                        Button(onClick = onStartGame) {
                            Text("START MATCH")
                        }
                    } else {
                        Text(
                            "Waiting for host to start...",
                            style = MaterialTheme.typography.headlineSmall,
                            color = Color.White,
                        )
                    }
                }

                GamePhase.PLAYER_TURN -> {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        uiState.gameState?.currentFaceUpCard?.let { card ->
                            Image(
                                painter = painterResource(id = getAnimalDrawable(card.type)),
                                contentDescription = null,
                                modifier = Modifier.size(150.dp),
                            )
                            Text(
                                card.type.name,
                                style = MaterialTheme.typography.headlineMedium,
                                color = Color.White,
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
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = Color.White.copy(alpha = 0.7f),
                                    modifier = Modifier.padding(top = 16.dp),
                                )
                            }
                        }
                    }
                }

                GamePhase.AUCTION -> {
                    AuctionView(uiState.gameState?.auctionState)
                }

                GamePhase.TRADE -> {
                    val trade = uiState.gameState?.tradeState
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            "TRADE CHALLENGE",
                            style = MaterialTheme.typography.headlineMedium,
                            color = MaterialTheme.colorScheme.tertiary,
                        )
                        if (trade != null) {
                            Text(
                                "Between ${trade.initiatingPlayerId} and ${trade.challengedPlayerId}",
                                style = MaterialTheme.typography.bodyLarge,
                            )
                            Text(
                                "For: ${trade.requestedAnimalType.name}",
                                style = MaterialTheme.typography.titleMedium,
                            )
                        }
                    }
                }

                GamePhase.FINISHED -> {
                    Text(
                        "GAME OVER",
                        style = MaterialTheme.typography.headlineLarge,
                        color = Color.White,
                    )
                }

                else -> {
                    Text("Current Phase: ${uiState.currentPhase}", color = Color.White)
                }
            }
        }

        // --- BOTTOM: PLAYER'S OWN AREA ---
        PlayerFarm(
            player = uiState.gameState?.players?.find { it.id == uiState.myPlayerId },
            isMyTurn = uiState.isMyTurn,
            modifier = Modifier.align(Alignment.BottomCenter),
        )
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
            phase = GamePhase.PLAYER_TURN,
            players = players,
            currentPlayerIndex = 4,
        )
    val uiState =
        GameUiState(
            gameState = gameState,
            myPlayerId = "5",
            currentPhase = GamePhase.PLAYER_TURN,
            deckCountText = "5",
            canRevealCard = true,
        )
    GameScreen(
        uiState = uiState,
        onStartGame = {},
        onRevealCard = {},
    )
}
