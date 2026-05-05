package at.aau.kuhhandel.app.ui.game

import at.aau.kuhhandel.app.network.game.GameRepository
import at.aau.kuhhandel.shared.enums.GamePhase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
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
    val auctionTimerSeconds: Int? = null,
)

@OptIn(ExperimentalCoroutinesApi::class)
class GameViewModel(
    private val repository: GameRepository,
    private val scope: CoroutineScope,
) {
    private val auctionTimerSeconds = repository.state
        .map { it.gameState?.auctionState?.timerEndTime }
        .distinctUntilChanged()
        .flatMapLatest { endTime ->
            if (endTime == null) return@flatMapLatest flowOf<Int?>(null)
            flow<Int?> {
                while (true) {
                    val remaining = ((endTime - System.currentTimeMillis()) / 1000).toInt().coerceAtLeast(0)
                    emit(remaining)
                    if (remaining <= 0) break
                    delay(250)
                }
            }
        }

    val uiState: StateFlow<GameUiState> =
        combine(repository.state, auctionTimerSeconds) { repoState, timer ->
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
                auctionTimerSeconds = timer,
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
