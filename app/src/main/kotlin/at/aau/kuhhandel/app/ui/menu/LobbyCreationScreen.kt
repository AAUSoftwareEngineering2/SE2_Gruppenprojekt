package at.aau.kuhhandel.app.ui.menu

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import at.aau.kuhhandel.app.ui.components.MenuBackground
import at.aau.kuhhandel.app.ui.components.MenuCard
import at.aau.kuhhandel.app.network.game.GameConnectionUiState

@Composable
fun RoomCreationScreen(
    modifier: Modifier = Modifier,
    connectionState: GameConnectionUiState,
    // suspend () -> Unit: suspend function that returns nothing (Unit ≈ void)
    onCreateLobby: suspend () -> Unit,
    onBack: () -> Unit,
    onLobbyCreated: (String) -> Unit,
) {
    val hasStarted = remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        if (!hasStarted.value) {
            hasStarted.value = true
            runCatching { onCreateLobby() }
        }
    }

    LaunchedEffect(connectionState.gameId) {
        connectionState.gameId?.let(onLobbyCreated)
    }

    MenuBackground(modifier = modifier) {
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(top = 64.dp, bottom = 32.dp, start = 32.dp, end = 32.dp),
            contentAlignment = Alignment.Center,
        ) {
            MenuCard(onBack = onBack) {
                when {
                    connectionState.errorMessage != null -> {
                        Text(
                            text = connectionState.errorMessage,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodyLarge,
                        )
                        Spacer(modifier = Modifier.height(24.dp))
                        Button(
                            onClick = onBack,
                            modifier = Modifier.fillMaxWidth(0.6f),
                        ) {
                            Text("Back")
                        }
                    }

                    connectionState.gameId != null -> {
                        Text(
                            text = "Lobby created!",
                            style = MaterialTheme.typography.headlineMedium,
                        )
                        Spacer(modifier = Modifier.height(24.dp))
                        Text(
                            text = "Lobby Code:",
                            style = MaterialTheme.typography.bodyLarge,
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = connectionState.gameId,
                            style = MaterialTheme.typography.displayMedium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Spacer(modifier = Modifier.height(32.dp))
                        Text(
                            text = "Share this code with other players\"",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }

                    else -> {
                        CircularProgressIndicator()
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text =
                                if (connectionState.isConnecting) {
                                    "Connection to Server..."
                                } else {
                                    "Create Lobby..."
                                },
                        )
                    }
                }
            }
        }
    }
}
