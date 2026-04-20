package at.aau.kuhhandel.app.ui.menu

import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import at.aau.kuhhandel.app.network.game.GameConnectionUiState
import at.aau.kuhhandel.app.ui.components.MenuBackground
import at.aau.kuhhandel.app.ui.components.MenuCard
import at.aau.kuhhandel.shared.enums.GamePhase

@Composable
fun LobbyScreen(
    modifier: Modifier = Modifier,
    lobbyCode: String,
    connectionState: GameConnectionUiState,
    onStartGame: () -> Unit,
    onRevealCard: () -> Unit,
    onDismissError: () -> Unit,
    onBack: () -> Unit,
) {
    val gameState = connectionState.gameState
    val resolvedLobbyCode = connectionState.gameId ?: lobbyCode
    val players =
        gameState
            ?.players
            ?.mapIndexed { index, playerState ->
                Player(
                    name = playerState.name.ifBlank { playerState.id },
                    isHost = index == 0,
                    isReady = connectionState.isConnected,
                )
            }.orEmpty()
            .ifEmpty {
                listOf(
                    Player("Du", true, connectionState.isConnected),
                )
            }
    val currentPhase = gameState?.phase
    val remainingCards = gameState?.deck?.size() ?: 0
    val currentCardLabel =
        gameState?.currentFaceUpCard?.let { card ->
            "${card.type.name} (#${card.id})"
        } ?: "Noch keine Karte aufgedeckt"

    MenuBackground(modifier = modifier) {
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(top = 64.dp, bottom = 32.dp, start = 32.dp, end = 32.dp),
            contentAlignment = Alignment.Center,
        ) {
            MenuCard(onBack = onBack) {
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .background(
                                color = MaterialTheme.colorScheme.primaryContainer,
                                shape = MaterialTheme.shapes.medium,
                            ).padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column {
                        Text(
                            "Lobby Code",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                        Text(
                            resolvedLobbyCode,
                            style = MaterialTheme.typography.headlineSmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                    }
                    Text(
                        "Share this code",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                Text(
                    text =
                        if (connectionState.isConnected) {
                            "Verbunden"
                        } else if (connectionState.isConnecting) {
                            "Verbinde..."
                        } else {
                            "Nicht verbunden"
                        },
                    style = MaterialTheme.typography.bodyMedium,
                    color =
                        if (connectionState.errorMessage == null) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.error
                        },
                )

                Spacer(modifier = Modifier.height(12.dp))

                if (connectionState.errorMessage != null) {
                    Text(
                        text = connectionState.errorMessage,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = onDismissError,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Fehler ausblenden")
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                }

                Column(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .background(
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                shape = MaterialTheme.shapes.medium,
                            ).padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        text = "Phase: ${currentPhase?.name ?: "Noch kein GameState"}",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        text = "Verbleibende Karten: $remainingCards",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        text = "Aktuelle Karte: $currentCardLabel",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                Text(
                    "Spieler (${players.size})",
                    style = MaterialTheme.typography.titleMedium,
                )

                Spacer(modifier = Modifier.height(12.dp))

                LazyColumn(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(players) { player ->
                        PlayerListItem(player)
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (currentPhase == GamePhase.NOT_STARTED) {
                        Button(
                            onClick = onStartGame,
                            modifier = Modifier.fillMaxWidth(),
                            enabled = connectionState.isConnected,
                        ) {
                            Text("Spiel starten")
                        }
                    } else if (currentPhase == GamePhase.PLAYER_TURN) {
                        Button(
                            onClick = onRevealCard,
                            modifier = Modifier.fillMaxWidth(),
                            enabled = connectionState.isConnected,
                        ) {
                            Text("Naechste Karte aufdecken")
                        }
                    } else if (currentPhase == GamePhase.FINISHED) {
                        Text(
                            "Spiel beendet",
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.align(Alignment.CenterHorizontally),
                        )
                    }

                    OutlinedButton(
                        onClick = onBack,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Cancel")
                    }
                }
            }
        }
    }
}

@Composable
private fun PlayerListItem(player: Player) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(24.dp),
                ).padding(12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f),
        ) {
            Box(
                modifier =
                    Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    player.name.first().toString(),
                    color = MaterialTheme.colorScheme.onPrimary,
                    style = MaterialTheme.typography.titleMedium,
                )
            }

            Spacer(modifier = Modifier.size(12.dp))

            Column {
                Text(
                    player.name,
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    if (player.isHost) "Host" else "Player",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        if (player.isReady) {
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = "Ready",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp),
            )
        }
    }
}

data class Player(
    val name: String,
    val isHost: Boolean,
    val isReady: Boolean,
)
