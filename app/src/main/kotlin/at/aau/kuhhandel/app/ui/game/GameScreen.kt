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
import at.aau.kuhhandel.app.ui.components.FarmColor
import at.aau.kuhhandel.app.ui.components.MainBackground
import at.aau.kuhhandel.app.ui.components.OtherFarm

@Composable
fun GameScreen(
    modifier: Modifier = Modifier,
    uiState: GameUiState,
    onStartGame: () -> Unit,
    onRevealCard: () -> Unit,
) {
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
                text = "Game Phase: ${uiState.currentPhase.name}",
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = uiState.deckCountText,
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                text = "Active Card: ${uiState.activeCardLabel}",
                style = MaterialTheme.typography.bodyMedium,
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (uiState.canStartGame) {
                Button(
                    onClick = onStartGame,
                    modifier = Modifier.weight(1f),
                    enabled = uiState.isConnected,
                ) {
                    Text("Start Game")
                }
            }

            if (uiState.canRevealCard) {
                Button(
                    onClick = onRevealCard,
                    modifier = Modifier.weight(1f),
                    enabled = uiState.isConnected,
                ) {
                    Text("Reveal Card")
                }
            }
        }
    }
}
