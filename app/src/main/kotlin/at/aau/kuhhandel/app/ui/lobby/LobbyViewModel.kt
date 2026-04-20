package at.aau.kuhhandel.app.ui.lobby

import at.aau.kuhhandel.app.network.game.GameRepository
import at.aau.kuhhandel.shared.enums.GamePhase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class PlayerDisplayItem(
    val name: String,
    val isHost: Boolean,
    val isReady: Boolean,
)

data class LobbyUiState(
    val lobbyCode: String = "",
    val players: List<PlayerDisplayItem> = emptyList(),
    val connectionStatus: String = "Not connected",
    val isError: Boolean = false,
    val errorMessage: String? = null,
    val canStartGame: Boolean = false,
)

class LobbyViewModel(
    private val repository: GameRepository,
    private val scope: CoroutineScope,
    private val initialLobbyCode: String,
) {
    val uiState: StateFlow<LobbyUiState> =
        repository.state
            .map { repoState ->
                val gameState = repoState.gameState
                val players =
                    gameState
                        ?.players
                        ?.mapIndexed { index, playerState ->
                            PlayerDisplayItem(
                                name = playerState.name.ifBlank { playerState.id },
                                isHost = index == 0,
                                isReady = repoState.isConnected,
                            )
                        }.orEmpty()
                        .ifEmpty {
                            listOf(
                                PlayerDisplayItem("You", true, repoState.isConnected),
                            )
                        }

                LobbyUiState(
                    lobbyCode = repoState.gameId ?: initialLobbyCode,
                    players = players,
                    connectionStatus =
                        when {
                            repoState.isConnected -> "Connected"
                            repoState.isConnecting -> "Connecting..."
                            else -> "Not connected"
                        },
                    isError = repoState.errorMessage != null,
                    errorMessage = repoState.errorMessage,
                    canStartGame = repoState.isConnected && (gameState?.phase == GamePhase.NOT_STARTED || gameState == null),
                )
            }.stateIn(
                scope = scope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = LobbyUiState(lobbyCode = initialLobbyCode),
            )

    fun startGame() {
        scope.launch {
            repository.startGame()
        }
    }

    fun clearError() {
        repository.clearError()
    }
}
