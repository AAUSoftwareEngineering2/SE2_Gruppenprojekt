package at.aau.kuhhandel.app.ui.game

import at.aau.kuhhandel.app.network.game.GameRepository
import at.aau.kuhhandel.shared.enums.GamePhase
import at.aau.kuhhandel.shared.model.GameState
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

/**
 * UI State for the Main Game Screen.
 * Contains everything needed to render the board and player actions.
 */
data class GameUiState(
    val gameState: GameState? = null,
    val myPlayerId: String? = null,
    val currentPhase: GamePhase = GamePhase.NOT_STARTED,
    val deckCountText: String = "0 cards left",
    val activeCardLabel: String = "No card revealed",
    val isConnected: Boolean = false,
    val canRevealCard: Boolean = false,
    val canStartGame: Boolean = false,
    val auctionTimerSeconds: Int? = null,
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

@OptIn(ExperimentalCoroutinesApi::class)
class GameViewModel(
    private val repository: GameRepository,
    private val scope: CoroutineScope,
) {
    private val auctionTimerSeconds =
        repository.state
            .map { it.gameState?.auctionState?.timerEndTime }
            .distinctUntilChanged()
            .flatMapLatest { endTime ->
                if (endTime == null) return@flatMapLatest flowOf<Int?>(null)
                flow<Int?> {
                    while (true) {
                        val remaining =
                            ((endTime - System.currentTimeMillis()) / 1000)
                                .toInt()
                                .coerceAtLeast(
                                    0,
                                )
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
                gameState = gameState,
                myPlayerId = repoState.myPlayerId,
                currentPhase = currentPhase,
                deckCountText = "${gameState?.deck?.size() ?: 0} cards left",
                activeCardLabel =
                    gameState?.currentFaceUpCard?.let { card ->
                        "${card.type.name} (#${card.id})"
                    } ?: "No card revealed",
                isConnected = repoState.isConnected,
                canRevealCard =
                    repoState.isConnected &&
                        currentPhase == GamePhase.PLAYER_TURN &&
                        (
                            gameState?.players?.getOrNull(gameState.currentPlayerIndex)?.id ==
                                repoState.myPlayerId
                        ),
                canStartGame = repoState.isConnected && currentPhase == GamePhase.NOT_STARTED,
                errorMessage = repoState.errorMessage,
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

    // --- CONTRACT EXPANSION ---
    // The following actions need to be added to the shared WebSocketType and implemented in GameWebSocketClient/Server

    fun placeBid(amount: Int) {
        // Logic for placing a bid will be implemented here
    }

    fun initiateTrade(targetPlayerId: String) {
        // Logic for initiating a trade will be implemented here
    }
}
