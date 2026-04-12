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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RoomJoiningScreen(
    modifier: Modifier = Modifier,
    onBack: () -> Unit,
    onLobbyJoined: (String) -> Unit,
) {
    val lobbyCode = remember { mutableStateOf("") }
    val showJoinDialog = remember { mutableStateOf(true) }
    val isLoading = remember { mutableStateOf(false) }
    val errorMessage = remember { mutableStateOf<String?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Lobby beitreten") },
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
            if (showJoinDialog.value) {
                JoinLobbyDialog(
                    lobbyCode = lobbyCode.value,
                    onCodeChange = { lobbyCode.value = it },
                    onJoin = {
                        if (lobbyCode.value.length == 5 && lobbyCode.value.all { it.isDigit() }) {
                            isLoading.value = true
                            // TODO: Hier Server-Verbindung implementieren
                            try {
                                // Simuliere Server-Response
                                Thread.sleep(500)
                                onLobbyJoined(lobbyCode.value)
                            } catch (e: Exception) {
                                errorMessage.value = "Fehler beim Beitreten: ${e.message}"
                                isLoading.value = false
                            }
                        } else {
                            errorMessage.value = "Bitte geben Sie " +
                                "einen gültigen 5-stelligen Code ein"
                        }
                    },
                    onDismiss = onBack,
                    isLoading = isLoading.value,
                    error = errorMessage.value,
                    onErrorDismiss = { errorMessage.value = null },
                )
            }

            if (isLoading.value) {
                CircularProgressIndicator()
                Spacer(modifier = Modifier.height(16.dp))
                Text("Verbinde zur Lobby...")
            }
        }
    }
}

@Composable
private fun JoinLobbyDialog(
    lobbyCode: String,
    onCodeChange: (String) -> Unit,
    onJoin: () -> Unit,
    onDismiss: () -> Unit,
    isLoading: Boolean,
    error: String?,
    onErrorDismiss: () -> Unit,
) {
    if (error != null) {
        AlertDialog(
            onDismissRequest = onErrorDismiss,
            title = { Text("Fehler") },
            text = { Text(error) },
            confirmButton = {
                TextButton(onClick = onErrorDismiss) {
                    Text("OK")
                }
            },
        )
    } else {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("Lobby-Code eingeben") },
            text = {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        "Geben Sie den 5-stelligen Lobby-Code ein",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    OutlinedTextField(
                        value = lobbyCode,
                        onValueChange = { newValue ->
                            // Nur Ziffern erlauben, max. 5 Zeichen
                            if (newValue.length <= 5 && newValue.all { it.isDigit() }) {
                                onCodeChange(newValue)
                            }
                        },
                        label = { Text("Code") },
                        placeholder = { Text("12345") },
                        enabled = !isLoading,
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    if (isLoading) {
                        Spacer(modifier = Modifier.height(16.dp))
                        CircularProgressIndicator()
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = onJoin,
                    enabled = lobbyCode.length == 5 && !isLoading,
                ) {
                    Text("Beitreten")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = onDismiss,
                    enabled = !isLoading,
                ) {
                    Text("Abbrechen")
                }
            },
        )
    }
}
