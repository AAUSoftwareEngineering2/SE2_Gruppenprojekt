package at.aau.kuhhandel.app.ui.menu

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun MainMenuScreen(modifier: Modifier = Modifier) {
    val currentScreen = remember { mutableStateOf<MenuScreenState>(MenuScreenState.Main) }

    when (currentScreen.value) {
        MenuScreenState.Main ->
            MainMenuContent(
                modifier = modifier,
                onLobbyErstellen = { currentScreen.value = MenuScreenState.RoomCreation },
                onLobbyBeitreten = { currentScreen.value = MenuScreenState.RoomJoining },
                onRegeln = { currentScreen.value = MenuScreenState.Rules },
            )
        MenuScreenState.RoomCreation ->
            RoomCreationScreen(
                modifier = modifier,
                onBack = { currentScreen.value = MenuScreenState.Main },
                onLobbyCreated = { lobbyCode ->
                    currentScreen.value = MenuScreenState.Lobby(lobbyCode)
                },
            )
        MenuScreenState.RoomJoining ->
            RoomJoiningScreen(
                modifier = modifier,
                onBack = { currentScreen.value = MenuScreenState.Main },
                onLobbyJoined = { lobbyCode ->
                    currentScreen.value = MenuScreenState.Lobby(lobbyCode)
                },
            )
        is MenuScreenState.Lobby ->
            LobbyScreen(
                modifier = modifier,
                lobbyCode = (currentScreen.value as MenuScreenState.Lobby).lobbyCode,
                onBack = { currentScreen.value = MenuScreenState.Main },
            )
        MenuScreenState.Rules ->
            RulesScreen(
                modifier = modifier,
                onBack = { currentScreen.value = MenuScreenState.Main },
            )
    }
}

@Composable
private fun MainMenuContent(
    modifier: Modifier = Modifier,
    onLobbyErstellen: () -> Unit,
    onLobbyBeitreten: () -> Unit,
    onRegeln: () -> Unit,
) {
    Column(
        modifier =
            modifier
                .fillMaxSize()
                // HIER: Wendet den hellen Creme-Hintergrund aus dem Theme an
                .background(MaterialTheme.colorScheme.background)
                .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = "Kuhhandel",
            style = MaterialTheme.typography.headlineLarge,
            modifier = Modifier.padding(bottom = 48.dp),
        )

        Button(
            onClick = onLobbyErstellen,
            modifier =
                Modifier
                    .fillMaxWidth(0.8f)
                    .height(48.dp),
        ) {
            Text(
                text = "Lobby erstellen",
                // HIER: Zwingt den Text dazu, strahlend weiß zu sein
                color = MaterialTheme.colorScheme.onPrimary,
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = onLobbyBeitreten,
            modifier =
                Modifier
                    .fillMaxWidth(0.8f)
                    .height(48.dp),
        ) {
            Text(
                text = "Lobby beitreten",
                // HIER: Zwingt den Text dazu, strahlend weiß zu sein
                color = MaterialTheme.colorScheme.onPrimary,
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        OutlinedButton(
            onClick = onRegeln,
            modifier =
                Modifier
                    .fillMaxWidth(0.8f)
                    .height(48.dp),
        ) {
            Text("Regeln")
        }
    }
}

sealed class MenuScreenState {
    data object Main : MenuScreenState()

    data object RoomCreation : MenuScreenState()

    data object RoomJoining : MenuScreenState()

    data class Lobby(val lobbyCode: String) : MenuScreenState()

    data object Rules : MenuScreenState()
}
