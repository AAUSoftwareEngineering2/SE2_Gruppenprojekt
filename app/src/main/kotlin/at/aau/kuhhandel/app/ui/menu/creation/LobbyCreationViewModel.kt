package at.aau.kuhhandel.app.ui.menu.creation

import at.aau.kuhhandel.app.network.game.GameRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class LobbyCreationUiState(
    val isConnecting: Boolean = false,
    val gameId: String? = null,
    val errorMessage: String? = null,
    val isCreated: Boolean = false,
)

class LobbyCreationViewModel(
    private val repository: GameRepository,
    private val scope: CoroutineScope,
) {
    val uiState: StateFlow<LobbyCreationUiState> =
        repository.state
            .map { repoState ->
                LobbyCreationUiState(
                    isConnecting = repoState.isConnecting,
                    gameId = repoState.gameId,
                    errorMessage = repoState.errorMessage,
                    isCreated = repoState.gameId != null,
                )
            }.stateIn(
                scope = scope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = LobbyCreationUiState(),
            )

    fun createLobby() {
        scope.launch {
            repository.createGame()
        }
    }
}
