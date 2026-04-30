package at.aau.kuhhandel.app.ui.game

import at.aau.kuhhandel.app.network.game.GameRepository
import at.aau.kuhhandel.shared.enums.GamePhase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class GameUiState(
    val currentPhase: GamePhase = GamePhase.NOT_STARTED,
    val deckCountText: String = "0 cards left",
    val activeCardLabel: String = "No card revealed",
    val isConnected: Boolean = false,
    val canRevealCard: Boolean = false,
    val canStartGame: Boolean = false,
)

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
                    currentPhase = currentPhase,
                    deckCountText = "${gameState?.deck?.size() ?: 0} cards left",
                    activeCardLabel =
                        gameState?.currentFaceUpCard?.let { card ->
                            "${card.type.name} (#${card.id})"
                        } ?: "No card revealed",
                    isConnected = repoState.isConnected,
                    canRevealCard = repoState.isConnected && currentPhase == GamePhase.PLAYER_TURN,
                    canStartGame = repoState.isConnected && currentPhase == GamePhase.NOT_STARTED,
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
}
