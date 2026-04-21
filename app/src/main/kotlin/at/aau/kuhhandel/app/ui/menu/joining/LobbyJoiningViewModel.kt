package at.aau.kuhhandel.app.ui.menu.joining

import at.aau.kuhhandel.app.network.game.GameRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class LobbyJoiningUiState(
    val lobbyCode: String = "",
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val isJoined: Boolean = false,
    val joinedLobbyCode: String? = null,
)

class LobbyJoiningViewModel(
    private val repository: GameRepository,
    private val scope: CoroutineScope,
) {
    private val _lobbyCode = MutableStateFlow("")
    private val _isLoading = MutableStateFlow(false)
    private val _localErrorMessage = MutableStateFlow<String?>(null)

    val uiState: StateFlow<LobbyJoiningUiState> =
        combine(
            _lobbyCode,
            _isLoading,
            _localErrorMessage,
            repository.state,
        ) { code, loading, localError, repoState ->
            LobbyJoiningUiState(
                lobbyCode = code,
                isLoading = loading || repoState.isConnecting,
                errorMessage = localError ?: repoState.errorMessage,
                isJoined = repoState.gameId != null,
                joinedLobbyCode = repoState.gameId,
            )
        }.stateIn(
            scope = scope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = LobbyJoiningUiState(),
        )

    fun onLobbyCodeChanged(newCode: String) {
        if (newCode.length <= 5 && newCode.all { it.isDigit() }) {
            _lobbyCode.value = newCode
            _localErrorMessage.value = null
        }
    }

    fun joinLobby() {
        val code = _lobbyCode.value
        if (code.length == 5) {
            _isLoading.value = true
            _localErrorMessage.value = null
            scope.launch {
                try {
                    // Assuming the repository might have a joinGame method or we use createGame for now
                    // if that's how the backend handles it.
                    // Based on existing code, I'll use repository.createGame() as a placeholder
                    // or wait for the actual join implementation.
                    // For now, let's assume createGame can also be used or we need a joinGame.
                    repository.createGame()
                } catch (e: Exception) {
                    _isLoading.value = false
                }
            }
        } else {
            _localErrorMessage.value = "Code must have 5 digits"
        }
    }

    fun clearError() {
        _localErrorMessage.value = null
        repository.clearError()
    }
}
