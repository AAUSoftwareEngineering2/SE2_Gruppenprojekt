package at.aau.kuhhandel.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import at.aau.kuhhandel.app.audio.MenuMusicPlayer
import at.aau.kuhhandel.app.network.game.GameRepository
import at.aau.kuhhandel.app.network.game.GameWebSocketClient
import at.aau.kuhhandel.app.ui.game.GameScreen
import at.aau.kuhhandel.app.ui.game.GameViewModel
import at.aau.kuhhandel.app.ui.menu.creation.LobbyCreationViewModel
import at.aau.kuhhandel.app.ui.menu.creation.RoomCreationScreen
import at.aau.kuhhandel.app.ui.menu.joining.LobbyJoiningViewModel
import at.aau.kuhhandel.app.ui.menu.joining.RoomJoiningScreen
import at.aau.kuhhandel.app.ui.menu.lobby.LobbyScreen
import at.aau.kuhhandel.app.ui.menu.lobby.LobbyViewModel
import at.aau.kuhhandel.app.ui.menu.main.MainMenuScreen
import at.aau.kuhhandel.app.ui.menu.rules.RulesScreen
import at.aau.kuhhandel.shared.enums.GamePhase

@Composable
fun KuhhandelApp(modifier: Modifier = Modifier) {
    val navController = rememberNavController()
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

    // Musiksteuerung
    val isGameStarted = currentPhase != null && currentPhase != GamePhase.NOT_STARTED

    // Handle Game State transitions via Navigation
    LaunchedEffect(currentPhase) {
        if (currentPhase != null && currentPhase != GamePhase.NOT_STARTED) {
            navController.navigate(Screen.Game) {
                // Pop up to Main to clear the backstack when game starts
                popUpTo(Screen.Main) { inclusive = false }
            }
        }
    }

    MenuMusicPlayer(isGameStarted = isGameStarted) {
        NavHost(
            navController = navController,
            startDestination = Screen.Main,
            modifier = modifier,
        ) {
            composable<Screen.Main> {
                MainMenuScreen(
                    onCreateLobby = { navController.navigate(Screen.RoomCreation) },
                    onJoinLobby = { navController.navigate(Screen.RoomJoining) },
                    onRules = { navController.navigate(Screen.Rules) },
                )
            }

            composable<Screen.RoomCreation> {
                val creationViewModel =
                    remember(repository, scope) {
                        LobbyCreationViewModel(repository, scope)
                    }
                val creationUiState by creationViewModel.uiState.collectAsState()

                RoomCreationScreen(
                    uiState = creationUiState,
                    onCreateLobby = creationViewModel::createLobby,
                    onBack = {
                        repository.disconnect()
                        navController.popBackStack(Screen.Main, inclusive = false)
                    },
                    onLobbyCreated = { lobbyCode ->
                        navController.navigate(Screen.Lobby(lobbyCode)) {
                            popUpTo(Screen.RoomCreation) { inclusive = true }
                        }
                    },
                )
            }

            composable<Screen.RoomJoining> {
                val joiningViewModel =
                    remember(repository, scope) {
                        LobbyJoiningViewModel(repository, scope)
                    }
                val joiningUiState by joiningViewModel.uiState.collectAsState()

                RoomJoiningScreen(
                    uiState = joiningUiState,
                    onLobbyCodeChanged = joiningViewModel::onLobbyCodeChanged,
                    onJoinLobby = joiningViewModel::joinLobby,
                    onBack = {
                        repository.disconnect()
                        navController.popBackStack(Screen.Main, inclusive = false)
                    },
                    onLobbyJoined = { lobbyCode ->
                        navController.navigate(Screen.Lobby(lobbyCode)) {
                            popUpTo(Screen.RoomJoining) { inclusive = true }
                        }
                    },
                )
            }

            composable<Screen.Lobby> { backStackEntry ->
                val route: Screen.Lobby = backStackEntry.toRoute()
                val lobbyViewModel =
                    remember(repository, scope, route.lobbyCode) {
                        LobbyViewModel(repository, scope, route.lobbyCode)
                    }
                val lobbyUiState by lobbyViewModel.uiState.collectAsState()

                LobbyScreen(
                    uiState = lobbyUiState,
                    onStartGame = lobbyViewModel::startGame,
                    onDismissError = lobbyViewModel::clearError,
                    onBack = {
                        repository.disconnect()
                        navController.popBackStack(Screen.Main, inclusive = false)
                    },
                )
            }

            composable<Screen.Rules> {
                RulesScreen(
                    onBack = { navController.popBackStack(Screen.Main, inclusive = false) },
                )
            }

            composable<Screen.Game> {
                val gameViewModel =
                    remember(repository, scope) {
                        GameViewModel(repository, scope)
                    }
                val gameUiState by gameViewModel.uiState.collectAsState()

                GameScreen(
                    uiState = gameUiState,
                    onStartGame = gameViewModel::startGame,
                    onRevealCard = gameViewModel::revealCard,
                    onPlaceBid = gameViewModel::placeBid,
                    onBuyBack = gameViewModel::buyBack,
                    onRespondToTrade = gameViewModel::respondToTrade,
                )
            }
        }
    }
}
