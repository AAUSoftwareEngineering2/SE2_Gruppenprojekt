package at.aau.kuhhandel.app.ui.game

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import at.aau.kuhhandel.app.network.game.GameConnectionUiState
import at.aau.kuhhandel.app.ui.components.FarmColor
import at.aau.kuhhandel.app.ui.components.MainBackground
import at.aau.kuhhandel.app.ui.components.OtherFarm
import at.aau.kuhhandel.shared.enums.GamePhase

@Composable
fun GameScreen(
    modifier: Modifier = Modifier,
    connectionState: GameConnectionUiState,
    onStartGame: () -> Unit,
    onRevealCard: () -> Unit,
) {
    val gameState = connectionState.gameState
    val currentPhase = gameState?.phase
    val remainingCards = gameState?.deck?.size() ?: 0
    val currentCardLabel =
        gameState?.currentFaceUpCard?.let { card ->
            "${card.type.name} (#${card.id})"
        } ?: "No card revealed"

    MainBackground(modifier = modifier)
    OtherFarm(farmColor = FarmColor.BLUE, onClick = {})
    // some placeholder overlay for Game Logic (to be integrated into the farm design later)
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Column(
            modifier =
                Modifier
                    .background(
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.8f),
                        shape = MaterialTheme.shapes.medium,
                    ).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = "Game Phase: ${currentPhase?.name ?: "UNKNOWN"}",
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = "Deck: $remainingCards cards left",
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                text = "Active Card: $currentCardLabel",
                style = MaterialTheme.typography.bodyMedium,
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (currentPhase == GamePhase.NOT_STARTED) {
                Button(
                    onClick = onStartGame,
                    modifier = Modifier.weight(1f),
                    enabled = connectionState.isConnected,
                ) {
                    Text("Start Game")
                }
            }

            if (currentPhase == GamePhase.PLAYER_TURN) {
                Button(
                    onClick = onRevealCard,
                    modifier = Modifier.weight(1f),
                    enabled = connectionState.isConnected,
                ) {
                    Text("Reveal Card")
                }
            }
        }
    }
}
