package at.aau.kuhhandel.app.ui.menu

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import at.aau.kuhhandel.app.network.game.GameConnectionUiState

@OptIn(ExperimentalMaterial3Api::class)
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

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Lobby erstellen") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Zurück",
                        )
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(
            modifier =
                modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
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
                        Text("Zurück")
                    }
                }
                connectionState.gameId != null -> {
                    Text(
                        text = "Lobby erstellt!",
                        style = MaterialTheme.typography.headlineMedium,
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                    Text(
                        text = "Lobby-Code:",
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
                        text = "Teile diesen Code mit anderen Spielern",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                else -> {
                    CircularProgressIndicator()
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text =
                            if (connectionState.isConnecting) {
                                "Verbinde zum Server..."
                            } else {
                                "Erstelle Lobby..."
                            },
                    )
                }
            }
        }
    }
}
