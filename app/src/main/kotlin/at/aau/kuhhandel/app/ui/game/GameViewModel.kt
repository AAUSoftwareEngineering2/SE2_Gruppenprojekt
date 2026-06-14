package at.aau.kuhhandel.app.ui.game

import at.aau.kuhhandel.app.network.game.GameRepository
import at.aau.kuhhandel.shared.enums.AnimalType
import at.aau.kuhhandel.shared.enums.GamePhase
import at.aau.kuhhandel.shared.model.GameStateView
import at.aau.kuhhandel.shared.model.MoneyCard
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
    val gameState: GameStateView? = null,
    val myPlayerId: String? = null,
    val currentPhase: GamePhase = GamePhase.NOT_STARTED,
    val deckCountText: String = "0",
    val activeCardLabel: String = "No card revealed",
    val isConnected: Boolean = false,
    val isReconnecting: Boolean = false,
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
    val isTradeHandFanned: Boolean = false,
    val isCounterOfferSelected: Boolean = false,
    val isTradeActionSubmitting: Boolean = false,
    val finalRanking: List<at.aau.kuhhandel.shared.utils.GameRankEntry> = emptyList(),
    val eyeIconPlayerId: String? = null,
    val eyeIconTimerSeconds: Int? = null,
    val alreadySpied: Boolean = false,
    val isEyeIconHighlighted: Boolean = false,
    val spyingTargetId: String? = null,
    val spyingTargetCards: List<MoneyCard>? = null,
    val localPlayerSpiedOn: Boolean = false,
    val spiedOnOpponentIds: List<String> = emptyList(),
) {
    /** Helper property to check if an auction is currently in progress. */
    val isAuctionActive: Boolean
        get() =
            currentPhase == GamePhase.AUCTION_BIDDING ||
                currentPhase == GamePhase.AUCTIONEER_DECISION ||
                currentPhase == GamePhase.AUCTION_RESULT

    /** Shows the trading overlay for a local selection or an active server trade phase. */
    val isTradeActive: Boolean
        get() =
            pendingTradeAnimalType != null ||
                currentPhase == GamePhase.TRADE_OFFER ||
                currentPhase == GamePhase.TRADE_RESPONSE ||
                currentPhase == GamePhase.TRADE_RESULT

    val isMyTurn: Boolean
        get() = myPlayerId != null && gameState?.currentPlayerId == myPlayerId

    val isAuctioneer: Boolean
        get() = gameState?.auctionState?.auctioneerId == myPlayerId

    val isTradeInitiator: Boolean
        get() = gameState?.tradeState?.initiatorId == myPlayerId

    val isTradeTarget: Boolean
        get() = gameState?.tradeState?.targetId == myPlayerId

    val showsTradeOfferHand: Boolean
        get() = currentPhase == GamePhase.TRADE_OFFER && isTradeInitiator

    val showsTradeResponseDecision: Boolean
        get() =
            currentPhase == GamePhase.TRADE_RESPONSE &&
                isTradeTarget &&
                !isCounterOfferSelected

    val showsTradeCounterHand: Boolean
        get() =
            currentPhase == GamePhase.TRADE_RESPONSE &&
                isTradeTarget &&
                isCounterOfferSelected

    val tradeOfferCardCount: Int?
        get() = gameState?.tradeState?.initiatorCardCount

    val tradeCounterOfferCardCount: Int?
        get() = gameState?.tradeState?.targetCardCount

    val tradeResultOfferCards: List<MoneyCard>
        get() = resultCards(gameState?.tradeState?.visibleInitiatorCards)

    val tradeResultCounterOfferCards: List<MoneyCard>
        get() = resultCards(gameState?.tradeState?.visibleTargetCards)

    val tradeResultOfferTotal: Int
        get() = tradeResultOfferCards.sumOf { it.value }

    val tradeResultCounterOfferTotal: Int
        get() = tradeResultCounterOfferCards.sumOf { it.value }

    val isCurrentlySpying: Boolean
        get() = spyingTargetId != null

    val canShowEyeIconOnOpponents: Boolean
        get() =
            currentPhase == GamePhase.PLAYER_CHOICE &&
                gameState?.currentPlayerId != myPlayerId

    val activePlayerName: String
        get() =
            if (isMyTurn) {
                "Your"
            } else {
                gameState?.opponents?.find { it.id == gameState.currentPlayerId }?.name ?: "Unknown"
            }

    private fun resultCards(cards: List<MoneyCard>?): List<MoneyCard> =
        if (currentPhase == GamePhase.TRADE_RESULT) {
            cards.orEmpty().sortedWith(compareBy<MoneyCard> { it.value }.thenBy { it.id })
        } else {
            emptyList()
        }
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
    private val isTradeHandFanned = MutableStateFlow(false)
    private val isCounterOfferSelected = MutableStateFlow(false)
    private val isTradeActionSubmitting = MutableStateFlow(false)
    private val eyeIconPlayerId = MutableStateFlow<String?>(null)
    private val eyeIconTimerSeconds = MutableStateFlow<Int?>(null)
    private var eyeTimerJob: kotlinx.coroutines.Job? = null
    private val isEyeIconHighlighted = MutableStateFlow(false)

    init {
        scope.launch {
            repository.state
                .map { repoState ->
                    TradeServerState(
                        phase = repoState.gameState?.phase,
                        myPlayerId = repoState.myPlayerId,
                        initiatorId = repoState.gameState?.tradeState?.initiatorId,
                        errorMessage = repoState.errorMessage,
                    )
                }.distinctUntilChanged()
                .collect(::synchronizeTradeUi)
        }

        // Listen exclusively to turn index changes to clear the eye icon when your turn starts
        scope.launch {
            repository.state
                .map { it.gameState?.currentPlayerIndex }
                .distinctUntilChanged()
                .collect { index ->
                    val state = repository.state.value
                    val activePlayerId =
                        state.gameState
                            ?.players
                            ?.getOrNull(index ?: -1)
                            ?.id

                    // If the turn moves onto us, clear out our local eye icon variables instantly
                    if (activePlayerId == state.myPlayerId && activePlayerId != null) {
                        clearEyeSelection()
                    }
                }
        }
    }

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
                combine(
                    selectedTargetPlayerId,
                    pendingTradeTargetPlayerId,
                    pendingTradeAnimalType,
                ) { targetId, pendingTargetId, pendingAnimalType ->
                    TradeSelectionState(targetId, pendingTargetId, pendingAnimalType)
                },
                isTradeHandFanned,
                isCounterOfferSelected,
                isTradeActionSubmitting,
            ) { selection, tradeHandFanned, counterOfferSelected, submitting ->
                TradeLocalState(
                    selection = selection,
                    isHandFanned = tradeHandFanned,
                    isCounterOfferSelected = counterOfferSelected,
                    isSubmitting = submitting,
                )
            },
            combine(
                isHandFanned,
                eyeIconPlayerId,
                eyeIconTimerSeconds,
                isEyeIconHighlighted,
            ) { fanned, eyePlayerId, eyeTimer, eyeHighlighted ->
                LocalUiStateBundle(fanned, eyePlayerId, eyeTimer, eyeHighlighted)
            },
        ) { repoState, timer, selectedIds, tradeLocalState, uiBundle ->
            val gameState = repoState.gameState
            val view = repoState.gameStateView
            val currentPhase = gameState?.phase ?: GamePhase.NOT_STARTED
            val activePlayerId =
                gameState
                    ?.players
                    ?.getOrNull(gameState.currentPlayerIndex)
                    ?.id
            val isMyTurn = activePlayerId == repoState.myPlayerId && repoState.myPlayerId != null
            val canSelectTradeTarget = currentPhase == GamePhase.PLAYER_CHOICE && isMyTurn
            val tradeSelection = tradeLocalState.selection
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
                gameState = view,
                myPlayerId = repoState.myPlayerId,
                currentPhase = currentPhase,
                deckCountText = "${view?.deckSize ?: 0}",
                activeCardLabel = "No card revealed",
                // This can be refined if currentFaceUpCard is added to view
                isConnected = repoState.isConnected,
                isReconnecting = repoState.isReconnecting,
                canRevealCard =
                    (
                        repoState.isConnected &&
                            currentPhase == GamePhase.PLAYER_CHOICE &&
                            (view?.currentPlayerId == repoState.myPlayerId)
                    ),
                canStartGame =
                    repoState.isConnected &&
                        currentPhase == GamePhase.NOT_STARTED &&
                        (view?.opponents?.size?.plus(1) ?: 0) >= 3 &&
                        view?.hostPlayerId == repoState.myPlayerId,
                errorMessage = repoState.errorMessage,
                auctionTimerSeconds = timer,
                myMoneyCards = view?.localPlayer?.moneyCards ?: emptyList(),
                selectedMoneyCardIds = selectedIds,
                sharedAnimalsWithSelectedPlayer = sharedAnimals,
                selectedTargetPlayerId = targetId,
                pendingTradeTargetPlayerId = tradeSelection.pendingTargetPlayerId,
                pendingTradeAnimalType = tradeSelection.pendingAnimalType,
                canSelectTradeTarget = canSelectTradeTarget,
                isHandFanned = uiBundle.isHandFanned,
                isTradeHandFanned = tradeLocalState.isHandFanned,
                isCounterOfferSelected = tradeLocalState.isCounterOfferSelected,
                isTradeActionSubmitting = tradeLocalState.isSubmitting,
                finalRanking = view?.finalRanking ?: emptyList(),
                eyeIconPlayerId = uiBundle.eyePlayerId,
                eyeIconTimerSeconds = uiBundle.eyeTimer,
                alreadySpied = view?.alreadySpied ?: false,
                isEyeIconHighlighted = uiBundle.isEyeHighlighted,
                spyingTargetId = view?.spyingTargetId,
                spyingTargetCards = view?.spyingTargetCards,
                localPlayerSpiedOn = view?.localPlayerSpiedOn ?: false,
                spiedOnOpponentIds = view?.spiedOnOpponentIds.orEmpty(),
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
        isHandFanned.update { current ->
            val nextState = !current

            // If we are fanning our hand out, hide and clear the active eye icon
            if (nextState) {
                clearEyeSelection()
            }

            nextState
        }
    }

    /** Collapses the normal game money hand when the player taps outside it. */
    fun collapseHand() {
        isHandFanned.value = false
    }

    /** Toggles the trade-specific money hand independently of the normal game hand. */
    fun toggleTradeHandFanned() {
        isTradeHandFanned.update { !it }
    }

    /** Collapses the trade money hand when the player taps elsewhere on the table. */
    fun collapseTradeHand() {
        isTradeHandFanned.value = false
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

    /** Submits the initiator's selected money cards. */
    fun submitTradeOffer() {
        val state = uiState.value
        if (!state.showsTradeOfferHand ||
            isTradeActionSubmitting.value
        ) {
            return
        }

        isTradeActionSubmitting.value = true
        scope.launch {
            try {
                repository.submitTradeMoney(selectedMoneyCardIds.value)
                clearSelection()
            } catch (_: Exception) {
                isTradeActionSubmitting.value = false
            }
        }
    }

    /** Reveals the challenged player's money hand for a counter-offer. */
    fun chooseCounterOffer() {
        val state = uiState.value
        if (!state.showsTradeResponseDecision || isTradeActionSubmitting.value) {
            return
        }

        clearSelection()
        isCounterOfferSelected.value = true
        isTradeHandFanned.value = true
    }

    /** Accepts the initiator's money and gives up the animals without countering. */
    fun takeTradeOffer() {
        val state = uiState.value
        if (!state.showsTradeResponseDecision || isTradeActionSubmitting.value) {
            return
        }

        isTradeActionSubmitting.value = true
        scope.launch {
            try {
                repository.respondToTrade(emptySet())
                clearSelection()
            } catch (_: Exception) {
                isTradeActionSubmitting.value = false
            }
        }
    }

    /** Submits the challenged player's selected counter-offer cards. */
    fun submitCounterOffer() {
        val state = uiState.value
        if (!state.showsTradeCounterHand ||
            isTradeActionSubmitting.value
        ) {
            return
        }

        isTradeActionSubmitting.value = true
        scope.launch {
            try {
                repository.respondToTrade(selectedMoneyCardIds.value)
                clearSelection()
            } catch (_: Exception) {
                isTradeActionSubmitting.value = false
            }
        }
    }

    /** Starts a trade challenge against a selected player for a specific animal. */
    fun initiateTrade(
        targetPlayerId: String,
        animalType: AnimalType,
    ) {
        if (isTradeActionSubmitting.value) {
            return
        }

        isTradeActionSubmitting.value = true
        scope.launch {
            try {
                repository.initiateTrade(targetPlayerId, animalType)
                clearSelection()
                selectedTargetPlayerId.value = null
            } catch (_: Exception) {
                pendingTradeTargetPlayerId.value = null
                pendingTradeAnimalType.value = null
                isTradeActionSubmitting.value = false
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
        initiateTrade(targetPlayerId, animalType)
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

    /** Sets the opponent player targeted by the local eye icon selection and runs its timer. */
    fun selectEyeTargetPlayer(targetPlayerId: String) {
        val state = uiState.value

        if (!state.canShowEyeIconOnOpponents) return
        if (targetPlayerId == state.myPlayerId) return

        // Collapse our own money hand to clear the center screen real estate
        collapseHand()

        // Reset the eye selection
        clearEyeSelection()

        // Update the local state with the newly selected opponent player ID
        eyeIconPlayerId.value = targetPlayerId

        // Start a fresh asynchronous countdown loop for 5 seconds
        eyeTimerJob =
            scope.launch {
                var remaining = 5
                while (remaining > 0) {
                    eyeIconTimerSeconds.value = remaining
                    delay(1000)
                    remaining--
                }
                // When the 5 seconds are up, clear the target selection entirely
                clearEyeSelection()
            }
    }

    /** Highlights the active eye icon with a circle, prepping the device for shaking. */
    fun highlightEyeIcon() {
        if (eyeIconPlayerId.value != null) {
            isEyeIconHighlighted.value = true
        }
    }

    /** Triggers the network spy request when a valid device shake gesture is detected. */
    fun onPhoneShake() {
        val state = uiState.value
        if (!state.isEyeIconHighlighted) return

        val targetPlayerId = state.eyeIconPlayerId ?: return

        if (state.myMoneyCards.isEmpty()) return

        if (state.alreadySpied || state.isCurrentlySpying) {
            return
        }

        clearEyeSelection()

        // Dispatch the action
        scope.launch {
            try {
                repository.spy(targetPlayerId)
            } catch (_: Exception) {
                // Managed by repository error state
            }
        }
    }

    /** Sends an outbound command over the network to catch any players spying on this farm. */
    fun catchSpy() {
        scope.launch {
            try {
                repository.catchSpy()
            } catch (_: Exception) {
                // Error managed by repository state
            }
        }
    }

    /** Cancels the active countdown timer and completely clears all eye icon tracking states. */
    private fun clearEyeSelection() {
        eyeTimerJob?.cancel()
        eyeIconPlayerId.value = null
        eyeIconTimerSeconds.value = null
        isEyeIconHighlighted.value = false
    }

    private data class TradeSelectionState(
        val activeTargetPlayerId: String?,
        val pendingTargetPlayerId: String?,
        val pendingAnimalType: AnimalType?,
    )

    private data class TradeLocalState(
        val selection: TradeSelectionState,
        val isHandFanned: Boolean,
        val isCounterOfferSelected: Boolean,
        val isSubmitting: Boolean,
    )

    private data class TradeServerState(
        val phase: GamePhase?,
        val myPlayerId: String?,
        val initiatorId: String?,
        val errorMessage: String?,
    )

    private data class LocalUiStateBundle(
        val isHandFanned: Boolean,
        val eyePlayerId: String?,
        val eyeTimer: Int?,
        val isEyeHighlighted: Boolean,
    )

    private fun synchronizeTradeUi(serverState: TradeServerState) {
        if (serverState.phase != GamePhase.PLAYER_CHOICE) {
            eyeTimerJob?.cancel()
            eyeIconPlayerId.value = null
            eyeIconTimerSeconds.value = null
            isEyeIconHighlighted.value = false
        }

        when (serverState.phase) {
            GamePhase.TRADE_OFFER -> {
                pendingTradeTargetPlayerId.value = null
                pendingTradeAnimalType.value = null
                clearSelection()
                isTradeHandFanned.value = serverState.initiatorId == serverState.myPlayerId
                isCounterOfferSelected.value = false
                isTradeActionSubmitting.value = false
            }

            GamePhase.TRADE_RESPONSE -> {
                clearSelection()
                isTradeHandFanned.value = false
                isCounterOfferSelected.value = false
                isTradeActionSubmitting.value = false
            }

            GamePhase.TRADE_RESULT -> {
                clearSelection()
                isTradeHandFanned.value = false
                isCounterOfferSelected.value = false
                isTradeActionSubmitting.value = false
            }

            else -> {
                if (serverState.errorMessage != null) {
                    pendingTradeTargetPlayerId.value = null
                    pendingTradeAnimalType.value = null
                    isTradeActionSubmitting.value = false
                }
                isTradeHandFanned.value = false
                isCounterOfferSelected.value = false
            }
        }
    }
}
