package at.aau.kuhhandel.app.ui.game

import at.aau.kuhhandel.app.network.game.GameRepository
import at.aau.kuhhandel.shared.enums.AnimalType
import at.aau.kuhhandel.shared.enums.GamePhase
import at.aau.kuhhandel.shared.model.GameState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
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
    val myMoneyCards: List<at.aau.kuhhandel.shared.model.MoneyCard> = emptyList(),
    val selectedMoneyCardIds: Set<String> = emptySet(),
    val sharedAnimalsWithSelectedPlayer: List<AnimalType> = emptyList(),
    val selectedTargetPlayerId: String? = null,
) {
    val isMyTurn: Boolean get() =
        gameState?.currentPlayerIndex?.let {
            gameState.players.getOrNull(it)?.id == myPlayerId
        } ?: false

    val isAuctioneer: Boolean get() =
        gameState?.auctionState?.auctioneerId == myPlayerId

    val activePlayerName: String get() =
        gameState?.let {
            it.players.getOrNull(it.currentPlayerIndex)?.name
        } ?: "Unknown"
}

/**
 * Interface to provide the current time, allowing for better testability.
 */
interface TimeProvider {
    fun currentTimeMillis(): Long
}

class SystemTimeProvider : TimeProvider {
    override fun currentTimeMillis(): Long = System.currentTimeMillis()
}

@OptIn(ExperimentalCoroutinesApi::class)
class GameViewModel(
    private val repository: GameRepository,
    private val scope: CoroutineScope,
    private val timeProvider: TimeProvider = SystemTimeProvider(),
) {
    private val selectedMoneyCardIds = MutableStateFlow<Set<String>>(emptySet())
    private val selectedTargetPlayerId = MutableStateFlow<String?>(null)

    private val auctionTimerSeconds =
        repository.state
            .map { it.gameState?.auctionState?.timerEndTime }
            .distinctUntilChanged()
            .flatMapLatest { endTime ->
                if (endTime == null) return@flatMapLatest flowOf<Int?>(null)
                flow<Int?> {
                    while (true) {
                        val remaining =
                            ((endTime - timeProvider.currentTimeMillis()) / 1000)
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
        combine(
            repository.state,
            auctionTimerSeconds,
            selectedMoneyCardIds,
            selectedTargetPlayerId,
        ) { repoState, timer, selectedIds, targetId ->
            val gameState = repoState.gameState
            val currentPhase = gameState?.phase ?: GamePhase.NOT_STARTED

            val sharedAnimals = if (targetId != null && gameState != null && repoState.myPlayerId != null) {
                val myAnimals = gameState.players.find { it.id == repoState.myPlayerId }?.animals?.map { it.type }?.toSet() ?: emptySet()
                val targetAnimals = gameState.players.find { it.id == targetId }?.animals?.map { it.type }?.toSet() ?: emptySet()
                myAnimals.intersect(targetAnimals).toList()
            } else {
                emptyList()
            }

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
                    (
                        repoState.isConnected &&
                            currentPhase == GamePhase.PLAYER_TURN &&
                            (
                                gameState?.players?.getOrNull(gameState.currentPlayerIndex)?.id ==
                                    repoState.myPlayerId
                            )
                    ),
                canStartGame = repoState.isConnected && currentPhase == GamePhase.NOT_STARTED,
                errorMessage = repoState.errorMessage,
                auctionTimerSeconds = timer,
                myMoneyCards =
                    gameState?.players?.find { it.id == repoState.myPlayerId }?.moneyCards
                        ?: emptyList(),
                selectedMoneyCardIds = selectedIds,
                sharedAnimalsWithSelectedPlayer = sharedAnimals,
                selectedTargetPlayerId = targetId,
            )
        }.stateIn(
            scope = scope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = GameUiState(),
        )

    fun toggleMoneyCardSelection(cardId: String) {
        selectedMoneyCardIds.update { current ->
            if (current.contains(cardId)) current - cardId else current + cardId
        }
    }

    fun clearSelection() {
        selectedMoneyCardIds.value = emptySet()
    }

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
        scope.launch {
            try {
                repository.placeBid(amount)
            } catch (_: Exception) {
                // Error handled by repository
            }
        }
    }

    fun buyBack(buyBack: Boolean) {
        scope.launch {
            try {
                repository.buyBack(buyBack)
            } catch (_: Exception) {
                // Error handled by repository
            }
        }
    }

    private fun offerTrade() {
        scope.launch {
            try {
                repository.offerTrade(selectedMoneyCardIds.value.toList())
                clearSelection()
            } catch (_: Exception) {
            }
        }
    }

    fun respondToTrade(accepted: Boolean) {
        scope.launch {
            try {
                if (uiState.value.gameState
                        ?.tradeState
                        ?.initiatingPlayerId ==
                    uiState.value.myPlayerId
                ) {
                    // If I'm the initiator, use offerTrade logic
                    repository.offerTrade(selectedMoneyCardIds.value.toList())
                } else {
                    repository.respondToTrade(accepted, selectedMoneyCardIds.value.toList())
                }
                clearSelection()
            } catch (_: Exception) {
            }
        }
    }

    fun initiateTrade(targetPlayerId: String, animalType: AnimalType) {
        scope.launch {
            try {
                repository.initiateTrade(targetPlayerId, animalType, selectedMoneyCardIds.value.toList())
                clearSelection()
                selectedTargetPlayerId.value = null
            } catch (_: Exception) {
            }
        }
    }

    fun selectTargetPlayer(playerId: String?) {
        selectedTargetPlayerId.value = playerId
    }
}
