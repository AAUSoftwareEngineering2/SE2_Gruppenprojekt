package at.aau.kuhhandel.app.ui.game

import at.aau.kuhhandel.app.network.game.GameRepository
import at.aau.kuhhandel.shared.enums.GamePhase
import at.aau.kuhhandel.shared.model.GameState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * UI State for the Main Game Screen.
 * Contains everything needed to render the board and player actions.
 */
data class GameUiState(
    val gameState: GameState? = null,
    val myPlayerId: String = "player-1", // TODO: Fetch real player ID from Session/Auth manager
    val currentPhase: GamePhase = GamePhase.NOT_STARTED,
    val deckCountText: String = "0 cards left",
    val activeCardLabel: String = "No card revealed",
    val isConnected: Boolean = false,
    val canRevealCard: Boolean = false,
    val canStartGame: Boolean = false,
    val errorMessage: String? = null,
) {
    val isMyTurn: Boolean get() =
        gameState?.currentPlayerIndex?.let {
            gameState.players.getOrNull(it)?.id == myPlayerId
        } ?: false

    val activePlayerName: String get() =
        gameState?.let {
            it.players.getOrNull(it.currentPlayerIndex)?.name
        } ?: "Unknown"
}

class GameViewModel(
    private val repository: GameRepository,
    private val scope: CoroutineScope,
) {
    val uiState: StateFlow<GameUiState> =
        repository.state
            .map { repoState ->
                val gameState = repoState.gameState
                val currentPhase = gameState?.phase ?: GamePhase.NOT_STARTED

                GameUiState(
                    gameState = gameState,
                    currentPhase = currentPhase,
                    deckCountText = "${gameState?.deck?.size() ?: 0} cards left",
                    activeCardLabel =
                        gameState?.currentFaceUpCard?.let { card ->
                            "${card.type.name} (#${card.id})"
                        } ?: "No card revealed",
                    isConnected = repoState.isConnected,
                    canRevealCard = repoState.isConnected && currentPhase == GamePhase.PLAYER_TURN,
                    canStartGame = repoState.isConnected && currentPhase == GamePhase.NOT_STARTED,
                    errorMessage = repoState.errorMessage,
                )
            }.stateIn(
                scope = scope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = GameUiState(),
            )

    fun startGame() {
        scope.launch {
            repository.startGame()
        }
    }

    fun revealCard() {
        scope.launch {
            repository.revealCard()
        }
    }

    // --- TODO: CONTRACT EXPANSION ---
    // The following actions need to be added to the shared WebSocketType and implemented in GameWebSocketClient/Server

    fun placeBid(amount: Int) {
        // TODO: repository.placeBid(amount)
    }

    fun initiateTrade(targetPlayerId: String) {
        // TODO: repository.initiateTrade(targetPlayerId)
    }
}
