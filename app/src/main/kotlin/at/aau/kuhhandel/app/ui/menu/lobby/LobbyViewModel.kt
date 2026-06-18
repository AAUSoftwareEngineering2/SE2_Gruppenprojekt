package at.aau.kuhhandel.app.ui.menu.lobby

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
    val isMe: Boolean = false,
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
                val view = repoState.gameStateView
                val playerCount = view?.let { 1 + it.opponents.size } ?: 0
                val players =
                    view
                        ?.let {
                            listOf(
                                PlayerDisplayItem(
                                    name = it.localPlayer.name.ifBlank { it.localPlayer.id },
                                    isHost = it.localPlayer.id == it.hostPlayerId,
                                    isReady = repoState.isConnected,
                                    isMe = true,
                                ),
                            ) +
                                it.opponents.map { opponent ->
                                    PlayerDisplayItem(
                                        name = opponent.name.ifBlank { opponent.id },
                                        isHost = opponent.id == it.hostPlayerId,
                                        isReady = repoState.isConnected,
                                        isMe = opponent.id == repoState.myPlayerId,
                                    )
                                }
                        }.orEmpty()
                        .ifEmpty {
                            listOf(
                                PlayerDisplayItem("You", true, repoState.isConnected, true),
                            )
                        }

                val isHost =
                    view?.hostPlayerId == repoState.myPlayerId ||
                        view == null

                LobbyUiState(
                    lobbyCode = repoState.gameId ?: initialLobbyCode,
                    players = players,
                    connectionStatus =
                        when {
                            repoState.isConnected -> "Connected"
                            repoState.isReconnecting -> "Reconnecting..."
                            repoState.isConnecting -> "Connecting..."
                            else -> "Not connected"
                        },
                    isError = repoState.errorMessage != null,
                    errorMessage = repoState.errorMessage,
                    canStartGame =
                        repoState.isConnected &&
                            isHost &&
                            (view?.phase == GamePhase.NOT_STARTED || view == null) &&
                            playerCount >= 2,
                )
            }.stateIn(
                scope = scope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = LobbyUiState(lobbyCode = initialLobbyCode),
            )

    fun startGame() {
        scope.launch {
            try {
                repository.startGame()
            } catch (e: Exception) {
                // Error handling via repository.state
            }
        }
    }

    fun clearError() {
        repository.clearError()
    }
}
