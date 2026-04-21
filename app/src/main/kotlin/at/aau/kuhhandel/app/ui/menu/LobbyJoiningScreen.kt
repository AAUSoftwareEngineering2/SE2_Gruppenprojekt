package at.aau.kuhhandel.app.ui.menu

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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import at.aau.kuhhandel.app.ui.components.MenuBackground
import at.aau.kuhhandel.app.ui.components.MenuCard

@Composable
fun RoomJoiningScreen(
    modifier: Modifier = Modifier,
    onBack: () -> Unit,
    onLobbyJoined: (String) -> Unit,
) {
    val lobbyCode = remember { mutableStateOf("") }
    val isLoading = remember { mutableStateOf(false) }
    val errorMessage = remember { mutableStateOf<String?>(null) }

    MenuBackground(modifier = modifier) {
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

                if (errorMessage.value != null) {
                    Text(
                        text = errorMessage.value!!,
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
                    value = lobbyCode.value,
                    onValueChange = { newValue ->
                        if (newValue.length <= 5 && newValue.all { it.isDigit() }) {
                            lobbyCode.value = newValue
                        }
                    },
                    label = { Text("Code") },
                    placeholder = { Text("12345") },
                    enabled = !isLoading.value,
                    singleLine = true,
                    shape = RoundedCornerShape(24.dp),
                    modifier = Modifier.fillMaxWidth(),
                )

                Spacer(modifier = Modifier.height(32.dp))

                if (isLoading.value) {
                    CircularProgressIndicator()
                } else {
                    Button(
                        onClick = {
                            if (lobbyCode.value.length == 5) {
                                isLoading.value = true
                                // Simulated join
                                onLobbyJoined(lobbyCode.value)
                            } else {
                                errorMessage.value = "Code must have 5 digits"
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = lobbyCode.value.length == 5,
                    ) {
                        Text("Enter")
                    }
                }
            }
        }
    }
}
