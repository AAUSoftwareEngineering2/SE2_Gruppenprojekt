package at.aau.kuhhandel.app.ui.menu

import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import at.aau.kuhhandel.app.R
import at.aau.kuhhandel.app.network.game.GameRepository
import at.aau.kuhhandel.app.network.game.GameWebSocketClient
import at.aau.kuhhandel.app.network.ping.PingService
import at.aau.kuhhandel.app.ui.components.MenuBackground
import at.aau.kuhhandel.app.ui.components.MenuButton
import at.aau.kuhhandel.app.ui.game.GameScreen
import at.aau.kuhhandel.app.ui.game.GameViewModel
import at.aau.kuhhandel.app.ui.lobby.LobbyViewModel
import at.aau.kuhhandel.app.ui.theme.DarkPurple
import at.aau.kuhhandel.shared.enums.GamePhase
import androidx.compose.runtime.collectAsState
import kotlinx.coroutines.launch

@Composable
fun MainMenuScreen(modifier: Modifier = Modifier) {
    val currentScreen = remember { mutableStateOf<MenuScreenState>(MenuScreenState.Main) }
    val scope = rememberCoroutineScope()
    val repository =
        remember(scope) {
            GameRepository(
                client = GameWebSocketClient(),
                scope = scope,
            )
        }

    val repositoryState by repository.state.collectAsState()
    val currentPhase = repositoryState.gameState?.phase

    if (currentPhase != null && currentPhase != GamePhase.NOT_STARTED) {
        val gameViewModel = remember(repository, scope) { GameViewModel(repository, scope) }
        val gameUiState by gameViewModel.uiState.collectAsState()

        GameScreen(
            modifier = modifier,
            uiState = gameUiState,
            onStartGame = gameViewModel::startGame,
            onRevealCard = gameViewModel::revealCard,
        )
    } else {
        when (val state = currentScreen.value) {
            MenuScreenState.Main ->
                MainMenuContent(
                    modifier = modifier,
                    onCreateLobby = { currentScreen.value = MenuScreenState.RoomCreation },
                    onJoinLobby = { currentScreen.value = MenuScreenState.RoomJoining },
                    onRules = { currentScreen.value = MenuScreenState.Rules },
                )

            MenuScreenState.RoomCreation ->
                RoomCreationScreen(
                    modifier = modifier,
                    repositoryState = repositoryState,
                    onCreateLobby = { repository.createGame() },
                    onBack = {
                        repository.disconnect()
                        currentScreen.value = MenuScreenState.Main
                    },
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

            is MenuScreenState.Lobby -> {
                val lobbyViewModel =
                    remember(repository, scope, state.lobbyCode) {
                        LobbyViewModel(repository, scope, state.lobbyCode)
                    }
                val lobbyUiState by lobbyViewModel.uiState.collectAsState()

                LobbyScreen(
                    modifier = modifier,
                    uiState = lobbyUiState,
                    onStartGame = lobbyViewModel::startGame,
                    onDismissError = lobbyViewModel::clearError,
                    onBack = {
                        repository.disconnect()
                        currentScreen.value = MenuScreenState.Main
                    },
                )
            }

            MenuScreenState.Rules ->
                RulesScreen(
                    modifier = modifier,
                    onBack = { currentScreen.value = MenuScreenState.Main },
                )
        }
    }
}

@Composable
private fun MainMenuContent(
    modifier: Modifier = Modifier,
    onCreateLobby: () -> Unit,
    onJoinLobby: () -> Unit,
    onRules: () -> Unit,
) {
    // ==========================================================
    // PARAMETERS
    // ==========================================================

    val signSize = 1.5f
    val signOffsetX = 170.dp
    val signOffsetY = 220.dp

    val buttonOffsetX = -0.8f
    val buttonOffsetY = -0.9f
    val buttonWidth = 0.40f
    val buttonSpacing = (-75).dp

    // ==========================================================
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    MenuBackground(modifier = modifier) {
        // Title
        // TODO: Fancy drawable later
        Text(
            text = "KUHHANDEL",
            style = MaterialTheme.typography.displayLarge,
            fontWeight = FontWeight.Bold,
            color = DarkPurple,
            modifier =
                Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 64.dp)
                    .scale(scaleX = 1f, scaleY = 1.6f),
        )

        // 2. CONTAINER (Sign + Buttons)
        Box(
            modifier =
                Modifier
                    .align(Alignment.Center)
                    .offset(x = signOffsetX, y = signOffsetY)
                    .scale(signSize)
                    .fillMaxWidth(),
            contentAlignment = Alignment.Center,
        ) {
            // Sign
            Image(
                painter = painterResource(id = R.drawable.mm_sign_no_buttons),
                contentDescription = "Sign",
                modifier = Modifier.fillMaxWidth(),
                contentScale = ContentScale.Fit,
            )

            // Buttons
            Column(
                modifier =
                    Modifier
                        .align(
                            BiasAlignment(
                                horizontalBias = buttonOffsetX,
                                verticalBias = buttonOffsetY,
                            ),
                        ).fillMaxWidth(buttonWidth),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(buttonSpacing),
            ) {
                MenuButton(R.drawable.mm_create_room_button, "Create", onCreateLobby)
                MenuButton(R.drawable.mm_join_room_button, "Join", onJoinLobby)
                MenuButton(R.drawable.mm_rules_button, "Rules", onRules)
            }
        }

        // DEBUG: Ping Server Button
        Button(
            onClick = {
                scope.launch {
                    val result = PingService().isServerReachable()
                    result
                        .onSuccess {
                            Toast
                                .makeText(
                                    context,
                                    "Server is reachable!",
                                    Toast.LENGTH_SHORT,
                                ).show()
                        }.onFailure { e ->
                            Toast
                                .makeText(
                                    context,
                                    "Server error: ${e.message}",
                                    Toast.LENGTH_SHORT,
                                ).show()
                        }
                }
            },
            modifier =
                Modifier
                    .align(Alignment.BottomEnd)
                    .padding(16.dp),
        ) {
            Text("Ping-Server")
        }
    }
}

sealed class MenuScreenState {
    data object Main : MenuScreenState()

    data object RoomCreation : MenuScreenState()

    data object RoomJoining : MenuScreenState()

    data class Lobby(
        val lobbyCode: String,
    ) : MenuScreenState()

    data object Rules : MenuScreenState()
}
