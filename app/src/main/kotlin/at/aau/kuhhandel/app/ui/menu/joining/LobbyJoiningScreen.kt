package at.aau.kuhhandel.app.ui.menu.joining

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import at.aau.kuhhandel.app.ui.components.MenuBackground
import at.aau.kuhhandel.app.ui.components.MenuCard
import at.aau.kuhhandel.app.ui.menu.joining.LobbyJoiningUiState

@Composable
fun RoomJoiningScreen(
    uiState: LobbyJoiningUiState,
    onLobbyCodeChanged: (String) -> Unit,
    onJoinLobby: () -> Unit,
    onBack: () -> Unit,
    onLobbyJoined: (String) -> Unit,
) {
    LaunchedEffect(uiState.isJoined, uiState.joinedLobbyCode) {
        if (uiState.isJoined && uiState.joinedLobbyCode != null) {
            onLobbyJoined(uiState.joinedLobbyCode)
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
                Text(
                    "Enter Lobby",
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.primary,
                )

                Spacer(modifier = Modifier.height(24.dp))

                if (uiState.errorMessage != null) {
                    Text(
                        text = uiState.errorMessage,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(bottom = 16.dp),
                    )
                }

                Text(
                    "Enter 5-digit Code here",
                    style = MaterialTheme.typography.bodyMedium,
                )

                Spacer(modifier = Modifier.height(16.dp))

                OutlinedTextField(
                    value = uiState.lobbyCode,
                    onValueChange = onLobbyCodeChanged,
                    label = { Text("Code") },
                    placeholder = { Text("12345") },
                    enabled = !uiState.isLoading,
                    singleLine = true,
                    shape = RoundedCornerShape(24.dp),
                    modifier = Modifier.fillMaxWidth(),
                )

                Spacer(modifier = Modifier.height(32.dp))

                if (uiState.isLoading) {
                    CircularProgressIndicator()
                } else {
                    Button(
                        onClick = onJoinLobby,
                        modifier = Modifier.fillMaxWidth(),
                        enabled = uiState.lobbyCode.length == 5,
                    ) {
                        Text("Enter")
                    }
                }
            }
        }
    }
}
