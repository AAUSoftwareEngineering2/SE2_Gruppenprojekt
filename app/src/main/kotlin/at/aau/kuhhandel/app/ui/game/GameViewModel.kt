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
    val deckCountText: String = "0",
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
    val pendingTradeTargetPlayerId: String? = null,
    val pendingTradeAnimalType: AnimalType? = null,
    val canSelectTradeTarget: Boolean = false,
    val isHandFanned: Boolean = false,
) {
    /** Helper property to check if an auction is currently in progress. */
    val isAuctionActive: Boolean
        get() =
            currentPhase == GamePhase.AUCTION_BIDDING ||
                currentPhase == GamePhase.AUCTIONEER_DECISION

    /** Shows the trading overlay for a local selection or an active server trade phase. */
    val isTradeActive: Boolean
        get() =
            pendingTradeAnimalType != null ||
                currentPhase == GamePhase.TRADE_OFFER ||
                currentPhase == GamePhase.TRADE_RESPONSE ||
                currentPhase == GamePhase.TRADE_RESULT

    val isMyTurn: Boolean
        get() =
            gameState?.currentPlayerIndex?.let {
                gameState.players.getOrNull(it)?.id == myPlayerId
            } ?: false

    val isAuctioneer: Boolean
        get() =
            gameState?.auctionState?.auctioneerId == myPlayerId

    val activePlayerName: String
        get() =
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
    private val pendingTradeTargetPlayerId = MutableStateFlow<String?>(null)
    private val pendingTradeAnimalType = MutableStateFlow<AnimalType?>(null)
    private val isHandFanned = MutableStateFlow(false)

    private val auctionTimerSeconds =
        repository.state
            .map { it.gameState?.auctionState?.timerEndTime }
            .distinctUntilChanged()
            .flatMapLatest { endTime ->
                if (endTime == null) return@flatMapLatest flowOf<Int?>(null)
                flow<Int?> {
                    // Calculate initial remaining seconds once, clamped to [0, 5]
                    // to handle server/client clock desync.
                    var remaining =
                        ((endTime - timeProvider.currentTimeMillis()) / 1000)
                            .toInt()
                            .coerceIn(0, 5)

                    while (remaining >= 0) {
                        emit(remaining)
                        if (remaining == 0) break
                        delay(1000)
                        remaining--
                    }
                }
            }

    /**
     * The combined UI state for the game screen.
     * Derives complex properties like 'canRevealCard', 'canStartGame', and shared animals
     * by combining repository state with local selection state.
     */
    val uiState: StateFlow<GameUiState> =
        combine(
            repository.state,
            auctionTimerSeconds,
            selectedMoneyCardIds,
            combine(
                selectedTargetPlayerId,
                pendingTradeTargetPlayerId,
                pendingTradeAnimalType,
            ) { targetId, pendingTargetId, pendingAnimalType ->
                TradeSelectionState(targetId, pendingTargetId, pendingAnimalType)
            },
            isHandFanned,
        ) { repoState, timer, selectedIds, tradeSelection, fanned ->
            val gameState = repoState.gameState
            val currentPhase = gameState?.phase ?: GamePhase.NOT_STARTED
            val activePlayerId =
                gameState
                    ?.players
                    ?.getOrNull(gameState.currentPlayerIndex)
                    ?.id
            val isMyTurn = activePlayerId == repoState.myPlayerId && repoState.myPlayerId != null
            val canSelectTradeTarget = currentPhase == GamePhase.PLAYER_CHOICE && isMyTurn
            val targetId = tradeSelection.activeTargetPlayerId.takeIf { canSelectTradeTarget }

            val sharedAnimals =
                if (targetId != null &&
                    gameState != null &&
                    repoState.myPlayerId != null
                ) {
                    val myAnimals =
                        gameState.players
                            .find { it.id == repoState.myPlayerId }
                            ?.animals
                            ?.map { it.type }
                            ?.toSet()
                            ?: emptySet()
                    val targetAnimals =
                        gameState.players
                            .find { it.id == targetId }
                            ?.animals
                            ?.map { it.type }
                            ?.toSet()
                            ?: emptySet()
                    myAnimals.intersect(targetAnimals).toList()
                } else {
                    emptyList()
                }

            GameUiState(
                gameState = gameState,
                myPlayerId = repoState.myPlayerId,
                currentPhase = currentPhase,
                deckCountText = "${gameState?.deck?.size() ?: 0}",
                activeCardLabel =
                    gameState?.currentFaceUpCard?.let { card ->
                        "${card.type.name} (#${card.id})"
                    } ?: "No card revealed",
                isConnected = repoState.isConnected,
                canRevealCard =
                    (
                        repoState.isConnected &&
                            currentPhase == GamePhase.PLAYER_CHOICE &&
                            (
                                gameState
                                    ?.players
                                    ?.getOrNull(
                                        gameState.currentPlayerIndex,
                                    )?.id ==
                                    repoState.myPlayerId
                            )
                    ),
                canStartGame =
                    repoState.isConnected &&
                        currentPhase == GamePhase.NOT_STARTED &&
                        (gameState?.players?.size ?: 0) >= 3 &&
                        gameState?.hostPlayerId == repoState.myPlayerId,
                errorMessage = repoState.errorMessage,
                auctionTimerSeconds = timer,
                myMoneyCards =
                    gameState?.players?.find { it.id == repoState.myPlayerId }?.moneyCards
                        ?: emptyList(),
                selectedMoneyCardIds = selectedIds,
                sharedAnimalsWithSelectedPlayer = sharedAnimals,
                selectedTargetPlayerId = targetId,
                pendingTradeTargetPlayerId = tradeSelection.pendingTargetPlayerId,
                pendingTradeAnimalType = tradeSelection.pendingAnimalType,
                canSelectTradeTarget = canSelectTradeTarget,
                isHandFanned = fanned,
            )
        }.distinctUntilChanged()
            .stateIn(
                scope = scope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = GameUiState(),
            )

    /** Toggles the selection of a money card for trading or bidding. */
    fun toggleMoneyCardSelection(cardId: String) {
        selectedMoneyCardIds.update { current ->
            if (current.contains(cardId)) current - cardId else current + cardId
        }
    }

    /** Toggles the money hand fanned state. */
    fun toggleHandFanned() {
        isHandFanned.update { !it }
    }

    /** Deselects all currently selected money cards. */
    fun clearSelection() {
        selectedMoneyCardIds.value = emptySet()
    }

    /** Requests to start the game session. */
    fun startGame() {
        scope.launch {
            repository.startGame()
        }
    }

    /** Requests to reveal a new card from the deck. */
    fun revealCard() {
        scope.launch {
            repository.revealCard()
        }
    }

    // --- CONTRACT EXPANSION ---
    // The following actions need to be added to the shared WebSocketType and implemented in GameWebSocketClient/Server

    /** Places a bid during an active auction phase. */
    fun placeBid(amount: Int) {
        scope.launch {
            try {
                repository.placeBid(amount)
            } catch (_: Exception) {
                // Error handled by repository
            }
        }
    }

    /** Performs the buy-back action for the auctioneer. */
    fun buyBack(buyBack: Boolean) {
        scope.launch {
            try {
                repository.buyBack(buyBack)
            } catch (_: Exception) {
                // Error handled by repository
            }
        }
    }

    /** Submits a counter-offer for an ongoing trade challenge. */
    fun respondToTrade() {
        scope.launch {
            try {
                if (uiState.value.gameState
                        ?.tradeState
                        ?.initiatorId ==
                    uiState.value.myPlayerId
                ) {
                    // If I'm the initiator, use offerTrade logic
//                    repository.offerTrade(selectedMoneyCardIds.value.toList())
                } else {
                    repository.respondToTrade(selectedMoneyCardIds.value.toSet())
                }
                clearSelection()
            } catch (_: Exception) {
            }
        }
    }

    /** Starts a trade challenge against a selected player for a specific animal. */
    fun initiateTrade(
        targetPlayerId: String,
        animalType: AnimalType,
    ) {
        scope.launch {
            try {
                repository.initiateTrade(
                    targetPlayerId,
                    animalType,
                    selectedMoneyCardIds.value.toSet(),
                )
                clearSelection()
                selectedTargetPlayerId.value = null
            } catch (_: Exception) {
            }
        }
    }

    /** Sets the target player for a potential trade. */
    fun selectTargetPlayer(playerId: String?) {
        if (playerId == null) {
            selectedTargetPlayerId.value = null
            return
        }

        val repoState = repository.state.value
        val gameState = repoState.gameState ?: return
        val myPlayerId = repoState.myPlayerId ?: return
        val activePlayerId = gameState.players.getOrNull(gameState.currentPlayerIndex)?.id

        if (gameState.phase != GamePhase.PLAYER_CHOICE || activePlayerId != myPlayerId) {
            return
        }

        if (playerId == myPlayerId || gameState.players.none { it.id == playerId }) {
            return
        }

        pendingTradeTargetPlayerId.value = null
        pendingTradeAnimalType.value = null
        selectedTargetPlayerId.value = playerId
    }

    /** Stores the selected trade animal locally without initiating a backend trade yet. */
    fun selectTradeAnimal(animalType: AnimalType) {
        val currentState = uiState.value
        val targetPlayerId = currentState.selectedTargetPlayerId ?: return

        if (animalType !in currentState.sharedAnimalsWithSelectedPlayer) {
            return
        }

        pendingTradeTargetPlayerId.value = targetPlayerId
        pendingTradeAnimalType.value = animalType
        selectedTargetPlayerId.value = null
    }

    /** Signals that the trade reveal animation has finished. */
    fun finishTradeReveal() {
        scope.launch {
            try {
                repository.finishTradeReveal()
            } catch (_: Exception) {
            }
        }
    }

    private data class TradeSelectionState(
        val activeTargetPlayerId: String?,
        val pendingTargetPlayerId: String?,
        val pendingAnimalType: AnimalType?,
    )
}
