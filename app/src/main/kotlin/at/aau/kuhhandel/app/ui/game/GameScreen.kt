package at.aau.kuhhandel.app.ui.game

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.unit.dp
import at.aau.kuhhandel.app.ui.components.AuctionView
import at.aau.kuhhandel.app.ui.components.MainBackground
import at.aau.kuhhandel.app.ui.components.OpponentList
import at.aau.kuhhandel.app.ui.components.PlayerFarm
import at.aau.kuhhandel.app.ui.components.getAnimalDrawable
import at.aau.kuhhandel.shared.enums.GamePhase

@Composable
fun GameScreen(
    uiState: GameUiState,
    onStartGame: () -> Unit,
    onRevealCard: () -> Unit,
    modifier: Modifier = Modifier,
) {
    MainBackground(modifier = modifier)

    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .padding(16.dp),
    ) {
        // --- TOP: OPPONENTS ---
        OpponentList(
            players = uiState.gameState?.players ?: emptyList(),
            myId = uiState.myPlayerId,
        )

        Spacer(modifier = Modifier.weight(1f))

        // --- CENTER: THE BOARD ---
        Box(
            modifier = Modifier.fillMaxWidth(),
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
                                modifier = Modifier.padding(top = 8.dp),
                            )
                        } ?: run {
                            // Deck back / Placeholder
                            Image(
                                painter =
                                    painterResource(
                                        id = at.aau.kuhhandel.app.R.drawable.ig_tall_bush,
                                    ),
                                // Placeholder for card back
                                contentDescription = "Deck",
                                modifier = Modifier.size(150.dp),
                                alpha = 0.5f,
                            )
                            Text("Deck", style = MaterialTheme.typography.headlineMedium)
                        }

                        Text(
                            uiState.deckCountText,
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.padding(top = 8.dp),
                        )

                        if (uiState.isMyTurn) {
                            Spacer(modifier = Modifier.height(24.dp))
                            Button(
                                onClick = onRevealCard,
                                modifier = Modifier.height(56.dp).fillMaxWidth(0.6f),
                            ) {
                                Text("REVEAL CARD")
                            }
                        } else {
                            Text(
                                "Waiting for ${uiState.activePlayerName}...",
                                style = MaterialTheme.typography.bodyLarge,
                                color = Color.Gray,
                                modifier = Modifier.padding(top = 16.dp),
                            )
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
                    Text("GAME OVER", style = MaterialTheme.typography.headlineLarge)
                }

                else -> {
                    Text("Current Phase: ${uiState.currentPhase}")
                }
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        // --- BOTTOM: PLAYER'S OWN AREA ---
        PlayerFarm(
            player = uiState.gameState?.players?.find { it.id == uiState.myPlayerId },
            isMyTurn = uiState.isMyTurn,
        )
    }
}
