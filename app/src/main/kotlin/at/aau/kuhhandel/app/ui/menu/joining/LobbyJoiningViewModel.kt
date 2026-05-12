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
    private val lobbyCode = MutableStateFlow("")
    private val isLoading = MutableStateFlow(false)
    private val localErrorMessage = MutableStateFlow<String?>(null)

    val uiState: StateFlow<LobbyJoiningUiState> =
        combine(
            lobbyCode,
            isLoading,
            localErrorMessage,
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
            lobbyCode.value = newCode
            localErrorMessage.value = null
        }
    }

    fun joinLobby() {
        val code = lobbyCode.value
        if (code.length == 5) {
            isLoading.value = true
            localErrorMessage.value = null
            scope.launch {
                try {
                    repository.joinGame(code) // re-check this! Fix placeholder
                    isLoading.value = false
                } catch (e: Exception) {
                    isLoading.value = false
                }
            }
        } else {
            localErrorMessage.value = "Code must have 5 digits"
        }
    }

    fun clearError() {
        localErrorMessage.value = null
        repository.clearError()
    }
}
