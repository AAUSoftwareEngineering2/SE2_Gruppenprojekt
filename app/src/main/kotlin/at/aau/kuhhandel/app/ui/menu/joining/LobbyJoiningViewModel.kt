package at.aau.kuhhandel.app.ui.menu.joining

import at.aau.kuhhandel.app.network.game.GameRepository
import at.aau.kuhhandel.shared.model.PlayerNameRules
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class LobbyJoiningUiState(
    val lobbyCode: String = "",
    val playerName: String = "",
    val playerNameError: String? = null,
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val isJoined: Boolean = false,
    val joinedLobbyCode: String? = null,
) {
    val canSubmit: Boolean
        get() = lobbyCode.length == 5 && PlayerNameRules.isValid(playerName) && !isLoading
}

class LobbyJoiningViewModel(
    private val repository: GameRepository,
    private val scope: CoroutineScope,
) {
    private val lobbyCode = MutableStateFlow("")
    private val playerName = MutableStateFlow("")
    private val playerNameError = MutableStateFlow<String?>(null)
    private val isLoading = MutableStateFlow(false)
    private val localErrorMessage = MutableStateFlow<String?>(null)

    val uiState: StateFlow<LobbyJoiningUiState> =
        combine(
            combine(lobbyCode, playerName, playerNameError) { code, name, nameError ->
                Triple(code, name, nameError)
            },
            isLoading,
            localErrorMessage,
            repository.state,
        ) { codeNameError, loading, localError, repoState ->
            val (code, name, nameError) = codeNameError
            LobbyJoiningUiState(
                lobbyCode = code,
                playerName = name,
                playerNameError = nameError,
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

    fun onPlayerNameChanged(newName: String) {
        if (newName.length > PlayerNameRules.MAX_LENGTH) return
        playerName.value = newName
        playerNameError.value = null
    }

    /** Validates the inputs and requests to join the game via the repository. */
    fun joinLobby() {
        val code = lobbyCode.value
        if (code.length != 5) {
            localErrorMessage.value = "Code must have 5 digits"
            return
        }

        val name = playerName.value.trim()
        val violation = PlayerNameRules.validate(name)
        if (violation != null) {
            playerNameError.value = violation.toUserMessage()
            return
        }
        playerName.value = name

        isLoading.value = true
        localErrorMessage.value = null
        playerNameError.value = null
        repository.clearError()
        scope.launch {
            try {
                repository.joinGame(code, name)
                // We don't set isLoading to false here immediately,
                // because we wait for the repository state to update
                // either with a gameId (success) or an errorMessage (failure).
                // However, to avoid being stuck in loading if something fails silently:
                kotlinx.coroutines.delay(1000)
                isLoading.value = false
            } catch (e: Exception) {
                isLoading.value = false
            }
        }
    }

    fun clearError() {
        localErrorMessage.value = null
        playerNameError.value = null
        repository.clearError()
    }
}

private fun PlayerNameRules.Violation.toUserMessage(): String =
    when (this) {
        PlayerNameRules.Violation.EMPTY -> "Please enter a player name"
        PlayerNameRules.Violation.TOO_LONG ->
            "Max ${PlayerNameRules.MAX_LENGTH} characters"
        PlayerNameRules.Violation.INVALID_CHARACTERS ->
            "Only letters and digits are allowed"
    }
