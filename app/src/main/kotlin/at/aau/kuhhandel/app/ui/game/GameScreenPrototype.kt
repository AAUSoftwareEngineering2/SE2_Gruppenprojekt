package at.aau.kuhhandel.app.ui.game

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import at.aau.kuhhandel.app.ui.components.MainBackground
import at.aau.kuhhandel.shared.enums.GamePhase
import at.aau.kuhhandel.shared.model.AuctionState
import at.aau.kuhhandel.shared.model.PlayerState

/**
 * PROTOTYPE IMPLEMENTATION - Main Game Screen
 * This is a functional prototype using placeholders for assets.
 *
 * TODO: Replace Icons (Star, Info, AccountCircle) with actual game assets (Animal types, Money bills).
 * TODO: Fetch the actual myPlayerId from a SessionManager or Auth context.
 * TODO: Implement interaction callbacks for Bidding and Trading once the server handles them.
 */
@Composable
fun GameScreenPrototype(
    uiState: GameUiState,
    onStartGame: () -> Unit,
    onRevealCard: () -> Unit,
    modifier: Modifier = Modifier,
) {
    MainBackground(modifier = modifier)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
    ) {
        // --- TOP: OPPONENTS ---
        OpponentListPrototype(
            players = uiState.gameState?.players ?: emptyList(),
            myId = uiState.myPlayerId
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
                            style = MaterialTheme.typography.headlineSmall
                        )
                    }
                }

                GamePhase.PLAYER_TURN -> {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.Star,
                            contentDescription = null,
                            modifier = Modifier.size(100.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Text(uiState.deckCountText, style = MaterialTheme.typography.titleMedium)

                        if (uiState.isMyTurn) {
                            Spacer(modifier = Modifier.height(16.dp))
                            // TODO: Add Choice between "Reveal Card (Auction)" and "Kuhhandel (Trade)"
                            Button(onClick = onRevealCard) {
                                Text("REVEAL CARD")
                            }
                        } else {
                            Text(
                                "Waiting for ${uiState.activePlayerName}...",
                                color = Color.Gray,
                                modifier = Modifier.padding(top = 8.dp)
                            )
                        }
                    }
                }

                GamePhase.AUCTION -> {
                    AuctionViewPrototype(uiState.gameState?.auctionState)
                }

                GamePhase.TRADE -> {
                    // TODO: Implement Trade/Kuhhandel interaction UI
                    Text(
                        "TRADE CHALLENGE",
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.tertiary
                    )
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
        PlayerFarmPrototype(
            player = uiState.gameState?.players?.find { it.id == uiState.myPlayerId },
            isMyTurn = uiState.isMyTurn
        )
    }
}

@Composable
fun OpponentListPrototype(players: List<PlayerState>, myId: String) {
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        items(players.filter { it.id != myId }) { player ->
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    imageVector = Icons.Default.AccountCircle,
                    contentDescription = null,
                    tint = Color.LightGray,
                    modifier = Modifier
                        .size(56.dp)
                        .border(2.dp, MaterialTheme.colorScheme.outline, CircleShape)
                )
                Text(player.name, style = MaterialTheme.typography.labelMedium)
                // TODO: Display owned animal types as small icons
                Text("${player.animals.size} Animals", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
fun AuctionViewPrototype(auction: AuctionState?) {
    if (auction == null) return

    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer
        ),
        elevation = CardDefaults.cardElevation(8.dp),
        modifier = Modifier.padding(horizontal = 32.dp)
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("LIVE AUCTION", style = MaterialTheme.typography.labelLarge)
            Spacer(modifier = Modifier.height(12.dp))

            Icon(Icons.Default.Star, contentDescription = null, modifier = Modifier.size(64.dp))
            Text(auction.auctionCard.type.name, style = MaterialTheme.typography.headlineMedium)

            HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

            Text(
                "Highest Bid: ${auction.highestBid}",
                style = MaterialTheme.typography.titleLarge
            )
            Text(
                "By: ${auction.highestBidderId ?: "No bids yet"}",
                style = MaterialTheme.typography.bodySmall
            )

            // TODO: Add numeric input and "Place Bid" button
            // TODO: Implement bidding logic in GameWebSocketClient and Server
        }
    }
}

@Composable
fun PlayerFarmPrototype(player: PlayerState?, isMyTurn: Boolean) {
    Surface(
        tonalElevation = 8.dp,
        shadowElevation = 4.dp,
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        border = if (isMyTurn) BorderStroke(3.dp, MaterialTheme.colorScheme.primary) else null
    ) {
        Row(
            modifier = Modifier.padding(20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.Info,
                contentDescription = "Stats",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(48.dp)
            )
            Spacer(modifier = Modifier.width(20.dp))
            Column {
                Text(
                    if (isMyTurn) "YOUR TURN" else "YOUR FARM",
                    style = MaterialTheme.typography.titleLarge,
                    color = if (isMyTurn) MaterialTheme.colorScheme.primary else Color.Unspecified
                )
                // TODO: Map MoneyCards to interactive UI (chips/cards)
                Text("${player?.moneyCards?.size ?: 0} Money Cards in hand", style = MaterialTheme.typography.bodyMedium)
                Text("Total: ${player?.totalMoney() ?: 0}", style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}
