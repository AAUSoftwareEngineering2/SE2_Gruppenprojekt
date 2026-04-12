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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RoomCreationScreen(
    modifier: Modifier = Modifier,
    onBack: () -> Unit,
    onLobbyCreated: (String) -> Unit,
) {
    val isLoading = remember { mutableStateOf(false) }
    val errorMessage = remember { mutableStateOf<String?>(null) }
    val lobbyCode = remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        isLoading.value = true
        try {
            // TODO: Hier Server-Verbindung implementieren
            // Für jetzt: Simuliere Server-Response
            val generatedCode = generateLobbyCode()
            lobbyCode.value = generatedCode
            isLoading.value = false
            // Auto-navigate nach kurzer Verzögerung
            Thread.sleep(500)
            onLobbyCreated(generatedCode)
        } catch (e: Exception) {
            errorMessage.value = "Fehler beim Erstellen der Lobby: ${e.message}"
            isLoading.value = false
        }
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
                isLoading.value -> {
                    CircularProgressIndicator()
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("Verbinde zum Server...")
                }
                errorMessage.value != null -> {
                    Text(
                        text = errorMessage.value ?: "",
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
                lobbyCode.value != null -> {
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
                        text = lobbyCode.value ?: "",
                        style = MaterialTheme.typography.displayMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(modifier = Modifier.height(32.dp))
                    Text(
                        text = "Teile diesen Code mit anderen Spielern",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }
    }
}

/**
 * Generiert einen 5-stelligen Zahlencode für die Lobby
 * @return 5-stelliger Code als String (z.B. "12345")
 */
fun generateLobbyCode(): String {
    return (10000..99999).random().toString()
}
