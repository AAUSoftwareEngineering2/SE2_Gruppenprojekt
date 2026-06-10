package at.aau.kuhhandel.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import at.aau.kuhhandel.app.audio.MenuMusicPlayer
import at.aau.kuhhandel.app.data.TokenStorage
import at.aau.kuhhandel.app.network.game.GameRepository
import at.aau.kuhhandel.app.network.game.GameWebSocketClient
import at.aau.kuhhandel.app.ui.game.GameScreen
import at.aau.kuhhandel.app.ui.game.GameViewModel
import at.aau.kuhhandel.app.ui.game.TradeActions
import at.aau.kuhhandel.app.ui.menu.creation.LobbyCreationViewModel
import at.aau.kuhhandel.app.ui.menu.creation.RoomCreationScreen
import at.aau.kuhhandel.app.ui.menu.joining.LobbyJoiningViewModel
import at.aau.kuhhandel.app.ui.menu.joining.RoomJoiningScreen
import at.aau.kuhhandel.app.ui.menu.lobby.LobbyScreen
import at.aau.kuhhandel.app.ui.menu.lobby.LobbyViewModel
import at.aau.kuhhandel.app.ui.menu.main.MainMenuScreen
import at.aau.kuhhandel.app.ui.menu.rules.RulesScreen
import at.aau.kuhhandel.shared.enums.GamePhase
import kotlinx.coroutines.launch

/** Root Composable that defines the navigation graph and handles screen transitions. */
@Composable
fun KuhhandelApp(modifier: Modifier = Modifier) {
    val navController = rememberNavController()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val tokenStorage = remember(context) { TokenStorage(context) }
    val repository =
        remember(scope) {
            GameRepository(
                client = GameWebSocketClient(),
                scope = scope,
                tokenStorage = tokenStorage,
            )
        }

    val repositoryState by repository.state.collectAsState()
    val currentPhase = repositoryState.gameState?.phase

    // Musiksteuerung
    val isGameStarted = currentPhase != null && currentPhase != GamePhase.NOT_STARTED

    // Handle Game State transitions via Navigation
    LaunchedEffect(isGameStarted) {
        if (isGameStarted) {
            navController.navigate(Screen.Game) {
                // Pop up to Main to clear the backstack when game starts
                popUpTo(Screen.Main) { inclusive = false }
                launchSingleTop = true
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
                        scope.launch {
                            repository.leaveGame()
                            navController.popBackStack(Screen.Main, inclusive = false)
                        }
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
                val tradeActions =
                    remember(gameViewModel) {
                        TradeActions(
                            selectTargetPlayer = gameViewModel::selectTargetPlayer,
                            selectAnimal = gameViewModel::selectTradeAnimal,
                            submitOffer = gameViewModel::submitTradeOffer,
                            chooseCounterOffer = gameViewModel::chooseCounterOffer,
                            takeOffer = gameViewModel::takeTradeOffer,
                            submitCounterOffer = gameViewModel::submitCounterOffer,
                            toggleHandFanned = gameViewModel::toggleTradeHandFanned,
                            collapseHand = gameViewModel::collapseTradeHand,
                        )
                    }

                GameScreen(
                    uiState = gameUiState,
                    onStartGame = gameViewModel::startGame,
                    onRevealCard = gameViewModel::revealCard,
                    onPlaceBid = gameViewModel::placeBid,
                    onBuyBack = gameViewModel::buyBack,
                    onSubmitAuctionPayment = gameViewModel::submitAuctionPayment,
                    tradeActions = tradeActions,
                    onToggleMoneyCard = gameViewModel::toggleMoneyCardSelection,
                    onToggleHandFanned = gameViewModel::toggleHandFanned,
                    onCollapseHand = gameViewModel::collapseHand,
                )
            }
        }
    }
}
