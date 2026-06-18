package at.aau.kuhhandel.app.ui.menu.creation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import at.aau.kuhhandel.app.audio.LocalButtonClickSound
import at.aau.kuhhandel.app.ui.components.MenuBackground
import at.aau.kuhhandel.app.ui.components.MenuCard

@Composable
fun RoomCreationScreen(
    uiState: LobbyCreationUiState,
    onPlayerNameChanged: (String) -> Unit,
    onCreateLobby: () -> Unit,
    onBack: () -> Unit,
    onLobbyCreated: (String) -> Unit,
) {
    val playClickSound = LocalButtonClickSound.current
    var hasTriggeredNavigation by remember { mutableStateOf(false) }

    LaunchedEffect(uiState.isCreated, uiState.gameId) {
        if (uiState.isCreated && uiState.gameId != null && !hasTriggeredNavigation) {
            kotlinx.coroutines.delay(1000)
            hasTriggeredNavigation = true
            onLobbyCreated(uiState.gameId)
        }
    }

    MenuBackground {
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(top = 64.dp, bottom = 32.dp, start = 32.dp, end = 32.dp),
            contentAlignment = Alignment.Center,
        ) {
            MenuCard(onBack = onBack) {
                when {
                    uiState.errorMessage != null -> {
                        Text(
                            text = uiState.errorMessage,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodyLarge,
                        )
                        Spacer(modifier = Modifier.height(24.dp))
                        Button(
                            onClick = {
                                playClickSound()
                                onBack()
                            },
                            modifier = Modifier.fillMaxWidth(0.6f),
                        ) {
                            Text("Back")
                        }
                    }

                    uiState.gameId != null -> {
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
                            text = uiState.gameId,
                            style = MaterialTheme.typography.displayMedium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Spacer(modifier = Modifier.height(32.dp))
                        Text(
                            text = "Share this code with other players",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }

                    uiState.isConnecting || uiState.isCreating -> {
                        CircularProgressIndicator()
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text =
                                if (uiState.isConnecting) {
                                    "Connection to Server..."
                                } else {
                                    "Creating Lobby..."
                                },
                        )
                    }

                    else -> {
                        Text(
                            "Create Lobby",
                            style = MaterialTheme.typography.headlineMedium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Spacer(modifier = Modifier.height(24.dp))
                        Text(
                            "Choose your player name",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        OutlinedTextField(
                            value = uiState.playerName,
                            onValueChange = onPlayerNameChanged,
                            label = { Text("Player name") },
                            placeholder = { Text("Player Name...") },
                            singleLine = true,
                            isError = uiState.playerNameError != null,
                            supportingText = {
                                Text(
                                    text =
                                        uiState.playerNameError
                                            ?: "Max 8 chars, letters and digits only",
                                )
                            },
                            keyboardOptions =
                                KeyboardOptions(
                                    capitalization = KeyboardCapitalization.None,
                                ),
                            shape = RoundedCornerShape(24.dp),
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Spacer(modifier = Modifier.height(24.dp))
                        Button(
                            onClick = {
                                playClickSound()
                                onCreateLobby()
                            },
                            enabled = uiState.canSubmit,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text("Create Lobby")
                        }
                    }
                }
            }
        }
    }
}
