package at.aau.kuhhandel.app.ui.menu.creation

import at.aau.kuhhandel.app.network.game.GameRepository
import at.aau.kuhhandel.shared.model.PlayerNameRules
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class LobbyCreationUiState(
    val playerName: String = "",
    val playerNameError: String? = null,
    val isConnecting: Boolean = false,
    val gameId: String? = null,
    val errorMessage: String? = null,
    val isCreated: Boolean = false,
) {
    val canSubmit: Boolean
        get() = PlayerNameRules.isValid(playerName) && !isConnecting && gameId == null
}

class LobbyCreationViewModel(
    private val repository: GameRepository,
    private val scope: CoroutineScope,
) {
    private val playerName = MutableStateFlow("")
    private val playerNameError = MutableStateFlow<String?>(null)

    val uiState: StateFlow<LobbyCreationUiState> =
        combine(
            playerName,
            playerNameError,
            repository.state,
        ) { name, nameError, repoState ->
            LobbyCreationUiState(
                playerName = name,
                playerNameError = nameError,
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

    fun onPlayerNameChanged(newName: String) {
        if (newName.length > PlayerNameRules.MAX_LENGTH) return
        playerName.value = newName
        playerNameError.value = null
    }

    /** Triggers the creation of a new game room on the server. */
    fun createLobby() {
        val name = playerName.value.trim()
        val violation = PlayerNameRules.validate(name)
        if (violation != null) {
            playerNameError.value = violation.toUserMessage()
            return
        }
        playerName.value = name
        playerNameError.value = null
        scope.launch {
            try {
                repository.createGame(name)
            } catch (e: Exception) {
                // Repository surfaces the error via state.errorMessage.
            }
        }
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
