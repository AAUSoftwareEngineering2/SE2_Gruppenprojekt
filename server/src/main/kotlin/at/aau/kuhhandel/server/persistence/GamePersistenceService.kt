package at.aau.kuhhandel.server.persistence

import at.aau.kuhhandel.server.persistence.entity.AuctionStateEntity
import at.aau.kuhhandel.server.persistence.entity.DeckCardEntity
import at.aau.kuhhandel.server.persistence.entity.GameEntity
import at.aau.kuhhandel.server.persistence.entity.GamePlayerEntity
import at.aau.kuhhandel.server.persistence.entity.PlayerAnimalEntity
import at.aau.kuhhandel.server.persistence.entity.PlayerMoneyEntity
import at.aau.kuhhandel.server.persistence.entity.TradeStateEntity
import at.aau.kuhhandel.server.persistence.entity.UserEntity
import at.aau.kuhhandel.server.persistence.mapper.GameStateMapper
import at.aau.kuhhandel.server.persistence.repository.AuctionStateRepository
import at.aau.kuhhandel.server.persistence.repository.DeckCardRepository
import at.aau.kuhhandel.server.persistence.repository.GamePlayerRepository
import at.aau.kuhhandel.server.persistence.repository.GameRepository
import at.aau.kuhhandel.server.persistence.repository.PlayerAnimalRepository
import at.aau.kuhhandel.server.persistence.repository.PlayerMoneyRepository
import at.aau.kuhhandel.server.persistence.repository.TradeStateRepository
import at.aau.kuhhandel.server.persistence.repository.UserRepository
import at.aau.kuhhandel.shared.model.GameState
import at.aau.kuhhandel.shared.model.MoneyCard
import at.aau.kuhhandel.shared.model.Player
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.security.MessageDigest

/**
 * Persists the prototype's minimal game state and reloads it for reconnect / restart scenarios.
 *
 * The implementation favours a small surface for Sprint 2 over fine-grained diffing — when a game
 * state is saved, child collections (deck, players' money/animals, auction/trade rows) are wiped
 * for that game and rewritten from the supplied [GameState]. Optimistic locking on [GameEntity] is
 * provided by JPA's `@Version` column.
 *
 * Game ids in the shared DTO are 5-digit strings, mirrored as `BIGINT` PKs in `games`.
 */
@Service
class GamePersistenceService(
    private val userRepository: UserRepository,
    private val gameRepository: GameRepository,
    private val gamePlayerRepository: GamePlayerRepository,
    private val deckCardRepository: DeckCardRepository,
    private val playerMoneyRepository: PlayerMoneyRepository,
    private val playerAnimalRepository: PlayerAnimalRepository,
    private val auctionStateRepository: AuctionStateRepository,
    private val tradeStateRepository: TradeStateRepository,
) {
    private val logger = LoggerFactory.getLogger(GamePersistenceService::class.java)

    @Transactional
    fun saveGameState(
        gameId: String,
        state: GameState,
        // Real player activity stamps "now"; the timeout sweeper passes null so it does not keep
        // an abandoned game looking alive (see [GameEntity.lastActivityAt]).
        activityAt: Long? = System.currentTimeMillis(),
    ) {
        logger.info("[DB WRITE] Saving game $gameId | phase=${state.phase}")
        val gameKey = gameId.toLongOrNull() ?: error("Game id must be numeric, was '$gameId'")
        val game = upsertGame(gameKey, state, activityAt)
        val playerEntities = syncPlayers(game, state.players)
        syncDeck(game, state)
        syncPlayerInventories(playerEntities, state.players)
        syncAuctionState(game, playerEntities, state)
        syncTradeState(game, playerEntities, state)
        game.activePlayer =
            state.players.getOrNull(state.currentPlayerIndex)?.let { active ->
                playerEntities[active.id]?.user
            }
        game.faceUpAnimalType = state.currentFaceUpCard?.type
        gameRepository.save(game)
        logger.info("[DB WRITE] Saved game $gameId successfully")
    }

    @Transactional(readOnly = true)
    fun loadGameState(gameId: String): GameState? {
        logger.info("[DB READ] Loading game $gameId from database")
        val gameKey = gameId.toLongOrNull() ?: return null
        val game = gameRepository.findById(gameKey).orElse(null) ?: return null
        val players = gamePlayerRepository.findByGameOrderBySeatOrderAsc(game)
        val animalsByPlayer =
            players.associate { player ->
                requireNotNull(player.id) to playerAnimalRepository.findByPlayer(player)
            }
        val moneyByPlayer =
            players.associate { player ->
                requireNotNull(player.id) to playerMoneyRepository.findByPlayer(player)
            }
        val deck = deckCardRepository.findByGameOrderByDrawOrderAsc(game)
        val auction = auctionStateRepository.findById(gameKey).orElse(null)
        val trade = tradeStateRepository.findById(gameKey).orElse(null)

        val result =
            GameStateMapper.toGameState(
                game = game,
                players = players,
                animalsByPlayer = animalsByPlayer,
                moneyByPlayer = moneyByPlayer,
                deck = deck,
                auction = auction,
                trade = trade,
            )
        logger.info("[DB READ] Loaded game $gameId | phase=${result.phase}")
        return result
    }

    @Transactional(readOnly = true)
    fun existsGame(gameId: String): Boolean {
        val gameKey = gameId.toLongOrNull() ?: return false
        return gameRepository.existsById(gameKey)
    }

    @Transactional
    fun saveGameStateWithReconnectToken(
        gameId: String,
        state: GameState,
        playerId: String,
        token: String,
        activityAt: Long? = System.currentTimeMillis(),
    ): Boolean {
        val gameKey = gameId.toLongOrNull() ?: return false
        saveGameState(gameId, state, activityAt)
        return storeReconnectTokenHash(gameKey, playerId, token)
    }

    /**
     * Runs [mutate] on the current persisted state inside one transaction that holds the game's
     * row lock (lock, load, mutate, save). Concurrent mutations from other pods wait on the lock,
     * which prevents lost updates.
     *
     * Returns the saved state, or null when the game does not exist. Exceptions from [mutate]
     * roll the transaction back.
     */
    @Transactional
    fun mutateGameState(
        gameId: String,
        activityAt: Long? = System.currentTimeMillis(),
        mutate: (GameState) -> GameState,
    ): GameState? {
        val gameKey = gameId.toLongOrNull() ?: return null
        gameRepository.findWithLockById(gameKey) ?: return null
        val current = loadGameState(gameId) ?: return null
        val next = mutate(current)
        saveGameState(gameId, next, activityAt)
        return next
    }

    /**
     * Runs [mutate] under the game row lock and deletes the game in the same transaction if the
     * resulting state is empty. This avoids a join landing between "last player left" and purge.
     */
    @Transactional
    fun mutateGameStateDeletingEmpty(
        gameId: String,
        activityAt: Long? = System.currentTimeMillis(),
        mutate: (GameState) -> GameState,
    ): GameState? {
        val gameKey = gameId.toLongOrNull() ?: return null
        val game = gameRepository.findWithLockById(gameKey) ?: return null
        val current = loadGameState(gameId) ?: return null
        val next = mutate(current)
        if (next.hasNoPlayers()) {
            deleteGameData(gameKey, game)
        } else {
            saveGameState(gameId, next, activityAt)
        }
        return next
    }

    /**
     * Applies a state mutation and stores a newly issued reconnect token in one transaction.
     */
    @Transactional
    fun mutateGameStateWithIssuedReconnectToken(
        gameId: String,
        playerId: String,
        token: String,
        activityAt: Long? = System.currentTimeMillis(),
        mutate: (GameState) -> GameState,
    ): GameState? {
        val gameKey = gameId.toLongOrNull() ?: return null
        gameRepository.findWithLockById(gameKey) ?: return null
        val current = loadGameState(gameId) ?: return null
        val next = mutate(current)
        saveGameState(gameId, next, activityAt)
        check(storeReconnectTokenHash(gameKey, playerId, token)) {
            "Could not store reconnect token for player $playerId in game $gameId"
        }
        return next
    }

    /**
     * Validates the old reconnect token, applies the reconnect mutation, and rotates the token while
     * holding the game/player row locks.
     */
    @Transactional
    fun mutateGameStateForReconnect(
        gameId: String,
        playerId: String,
        token: String,
        newToken: String,
        activityAt: Long? = System.currentTimeMillis(),
        mutate: (GameState) -> GameState,
    ): ReconnectTokenMutationResult? {
        val gameKey = gameId.toLongOrNull() ?: return null
        gameRepository.findWithLockById(gameKey) ?: return null
        val player =
            gamePlayerRepository.findLockedByGameIdAndPlayerId(gameKey, playerId)
                ?: return ReconnectTokenMutationResult.InvalidToken
        val storedHash = player.reconnectTokenHash ?: return ReconnectTokenMutationResult.InvalidToken
        if (!tokenMatches(storedHash, token)) {
            return ReconnectTokenMutationResult.InvalidToken
        }

        val current = loadGameState(gameId) ?: return null
        val next = mutate(current)
        saveGameState(gameId, next, activityAt)
        player.reconnectTokenHash = hashToken(newToken)
        gamePlayerRepository.save(player)
        return ReconnectTokenMutationResult.Success(next)
    }

    /**
     * Game ids whose phase timer expired (timer_end <= now).
     */
    @Transactional(readOnly = true)
    fun findGameIdsWithExpiredTimers(now: Long): List<String> =
        gameRepository.findIdsWithExpiredTimer(now).map { it.toString() }

    /**
     * Game ids whose last player activity is older than [cutoff] (or was never recorded).
     */
    @Transactional(readOnly = true)
    fun findStaleGameIds(cutoff: Long): List<String> =
        gameRepository.findIdsByLastActivityBefore(cutoff).map { it.toString() }

    /**
     * Game ids with at least one active spy whose expiration deadline has passed.
     */
    @Transactional(readOnly = true)
    fun findGameIdsWithExpiredSpies(now: Long): List<String> =
        gameRepository.findIdsWithExpiredSpies(now).map { it.toString() }

    /**
     * Last time a real player acted on the game, or null when unknown.
     */
    @Transactional(readOnly = true)
    fun lastActivityAt(gameId: String): Long? {
        val gameKey = gameId.toLongOrNull() ?: return null
        return gameRepository.findById(gameKey).orElse(null)?.lastActivityAt
    }

    /**
     * Stores only a SHA-256 hash of [token] on the player's row. Reconnect tokens are bearer
     * credentials; hashing keeps a database dump from exposing immediately reusable tokens.
     * Returns false when the game or player is unknown.
     */
    @Transactional
    fun storeReconnectToken(
        gameId: String,
        playerId: String,
        token: String,
    ): Boolean {
        val gameKey = gameId.toLongOrNull() ?: return false
        return storeReconnectTokenHash(gameKey, playerId, token)
    }

    /**
     * The stored token hash for a player, or null when unknown. Every reconnect rotates the
     * token, so a changed hash means the player already reconnected somewhere.
     */
    @Transactional(readOnly = true)
    fun reconnectTokenFingerprint(
        gameId: String,
        playerId: String,
    ): String? {
        val gameKey = gameId.toLongOrNull() ?: return null
        return gamePlayerRepository.findByGameIdAndPlayerId(gameKey, playerId)?.reconnectTokenHash
    }

    /**
     * Validates a reconnect token against the persisted hash.
     */
    @Transactional(readOnly = true)
    fun isReconnectTokenValid(
        gameId: String,
        playerId: String,
        token: String,
    ): Boolean {
        val gameKey = gameId.toLongOrNull() ?: return false
        val stored =
            gamePlayerRepository.findByGameIdAndPlayerId(gameKey, playerId)?.reconnectTokenHash
                ?: return false
        return tokenMatches(stored, token)
    }

    private fun storeReconnectTokenHash(
        gameKey: Long,
        playerId: String,
        token: String,
    ): Boolean {
        val player = gamePlayerRepository.findByGameIdAndPlayerId(gameKey, playerId) ?: return false
        player.reconnectTokenHash = hashToken(token)
        gamePlayerRepository.save(player)
        return true
    }

    private fun tokenMatches(
        storedHash: String,
        token: String,
    ): Boolean = MessageDigest.isEqual(storedHash.toByteArray(), hashToken(token).toByteArray())

    private fun hashToken(token: String): String =
        MessageDigest
            .getInstance("SHA-256")
            .digest(token.toByteArray())
            .joinToString("") { "%02x".format(it) }

    @Transactional
    fun deleteGame(gameId: String) {
        logger.info("[DB DELETE] Deleting game $gameId from database")
        val gameKey = gameId.toLongOrNull() ?: return
        val game = gameRepository.findById(gameKey).orElse(null) ?: return
        deleteGameData(gameKey, game)
    }

    private fun deleteGameData(
        gameKey: Long,
        game: GameEntity,
    ) {
        auctionStateRepository.deleteById(gameKey)
        tradeStateRepository.deleteById(gameKey)
        deckCardRepository.deleteByGame(game)
        gamePlayerRepository
            .findByGameOrderBySeatOrderAsc(game)
            .forEach { player ->
                playerMoneyRepository.deleteByPlayer(player)
                playerAnimalRepository.deleteByPlayer(player)
            }
        gamePlayerRepository.deleteByGame(game)
        gameRepository.deleteById(gameKey)
    }

    private fun upsertGame(
        gameKey: Long,
        state: GameState,
        activityAt: Long?,
    ): GameEntity {
        val existing = gameRepository.findById(gameKey).orElse(null)
        return if (existing == null) {
            gameRepository.save(
                GameEntity(
                    id = gameKey,
                    status = GameStateMapper.toGameStatus(state.phase),
                    phase = state.phase,
                    timerEnd = state.timerEnd,
                    hostPlayerId = state.hostPlayerId,
                    roundNumber = state.roundNumber,
                    faceUpAnimalType = state.currentFaceUpCard?.type,
                    lastActivityAt = activityAt ?: System.currentTimeMillis(),
                    activeSpiesJson = GameStateMapper.encodeSpies(state.activeSpies),
                    spiedThisTurnJson =
                        GameStateMapper.encodeStringList(state.spiedThisTurn.toList()),
                    earliestSpyExpiry = state.activeSpies.minOfOrNull { it.expiresAt },
                ),
            )
        } else {
            existing.status = GameStateMapper.toGameStatus(state.phase)
            existing.phase = state.phase
            existing.timerEnd = state.timerEnd
            existing.hostPlayerId = state.hostPlayerId
            existing.roundNumber = state.roundNumber
            existing.faceUpAnimalType = state.currentFaceUpCard?.type
            existing.activeSpiesJson = GameStateMapper.encodeSpies(state.activeSpies)
            existing.spiedThisTurnJson =
                GameStateMapper.encodeStringList(state.spiedThisTurn.toList())
            existing.earliestSpyExpiry = state.activeSpies.minOfOrNull { it.expiresAt }
            // null = timeout sweep -> leave the timestamp untouched so the game can go stale.
            if (activityAt != null) existing.lastActivityAt = activityAt
            existing
        }
    }

    private fun syncPlayers(
        game: GameEntity,
        players: List<Player>,
    ): Map<String, GamePlayerEntity> {
        val existing = gamePlayerRepository.findByGameOrderBySeatOrderAsc(game)
        val existingByPlayerId = existing.associateBy { it.persistedPlayerId() }
        val incomingPlayerIds = players.map { it.id }.toSet()

        // Drop players no longer in the game, including their inventories.
        existing
            .filter { it.persistedPlayerId() !in incomingPlayerIds }
            .forEach { stale ->
                playerMoneyRepository.deleteByPlayer(stale)
                playerAnimalRepository.deleteByPlayer(stale)
                gamePlayerRepository.delete(stale)
            }

        // Seat order is reassigned positionally on every save (e.g. after startGame shuffles the
        // player list). Because (game_id, seat_order) is unique, reassigning seats in place can hit a
        // transient duplicate during flush: Hibernate runs inserts/updates before deletes, so a row
        // can be moved onto a seat another row has not vacated yet. Park the continuing players on
        // temporary, out-of-range negative seats and flush first, so the final assignment below can
        // never collide with an old seat value (negatives never overlap the final 0..n-1 range).
        val continuing = existing.filter { it.persistedPlayerId() in incomingPlayerIds }
        val desiredSeatByPlayerId =
            players.withIndex().associate { (index, player) ->
                player.id to index
            }
        val needsSeatReorder =
            continuing.any { entity ->
                desiredSeatByPlayerId[entity.persistedPlayerId()] != entity.seatOrder
            }
        if (needsSeatReorder) {
            continuing.forEachIndexed { index, entity -> entity.seatOrder = -(index + 1) }
            gamePlayerRepository.flush()
        }

        return players
            .mapIndexed { index, player ->
                val playerEntity = existingByPlayerId[player.id]
                val user = upsertUser(player.id)
                val saved =
                    if (playerEntity == null) {
                        gamePlayerRepository.save(
                            GamePlayerEntity(
                                game = game,
                                user = user,
                                playerId = player.id,
                                displayName = player.name,
                                seatOrder = index,
                                isConnected = player.isConnected,
                            ),
                        )
                    } else {
                        playerEntity.user = user
                        playerEntity.playerId = player.id
                        playerEntity.displayName = player.name
                        playerEntity.seatOrder = index
                        playerEntity.isConnected = player.isConnected
                        playerEntity
                    }
                player.id to saved
            }.toMap()
    }

    private fun upsertUser(playerId: String): UserEntity {
        val existing = userRepository.findByUsername(playerId)
        return if (existing != null) {
            userRepository.save(existing)
        } else {
            userRepository.save(UserEntity(username = playerId, passwordHash = ""))
        }
    }

    private fun syncDeck(
        game: GameEntity,
        state: GameState,
    ) {
        deckCardRepository.deleteByGame(game)
        state.deck.cards.forEachIndexed { index, card ->
            deckCardRepository.save(
                DeckCardEntity(
                    game = game,
                    animalType = card.type,
                    drawOrder = index,
                ),
            )
        }
    }

    private fun syncPlayerInventories(
        playerEntities: Map<String, GamePlayerEntity>,
        players: List<Player>,
    ) {
        players.forEach { player ->
            val entity =
                playerEntities[player.id]
                    ?: error("No persisted GamePlayer for ${player.id}")
            playerMoneyRepository.deleteByPlayer(entity)
            playerAnimalRepository.deleteByPlayer(entity)

            player.moneyCards
                .groupingBy { it.value }
                .eachCount()
                .forEach { (value, count) ->
                    playerMoneyRepository.save(
                        PlayerMoneyEntity(player = entity, cardValue = value, amount = count),
                    )
                }

            player.animals
                .groupingBy { it.type }
                .eachCount()
                .forEach { (type, count) ->
                    playerAnimalRepository.save(
                        PlayerAnimalEntity(player = entity, animalType = type, amount = count),
                    )
                }
        }
    }

    private fun syncAuctionState(
        game: GameEntity,
        playerEntities: Map<String, GamePlayerEntity>,
        state: GameState,
    ) {
        val auction = state.auctionState
        val existing = auctionStateRepository.findById(game.id).orElse(null)

        if (auction == null) {
            if (existing != null) auctionStateRepository.deleteById(game.id)
            return
        }

        val passedJson = GameStateMapper.encodeStringList(auction.excludedPlayerIds.toList())
        val highestBidder =
            auction.highestBidderId?.let { bidderId -> playerEntities[bidderId] }

        if (existing == null) {
            auctionStateRepository.save(
                AuctionStateEntity(
                    game = game,
                    currentAnimal = auction.auctionCard.type,
                    auctioneerPlayerId = auction.auctioneerId,
                    highestBid = auction.highestBid,
                    highestBidder = highestBidder,
                    passedPlayersJson = passedJson,
                    timerEndTime = auction.timerEndTime,
                    buyerPlayerId = auction.buyerId,
                    sellerPlayerId = auction.sellerId,
                ),
            )
        } else {
            existing.currentAnimal = auction.auctionCard.type
            existing.auctioneerPlayerId = auction.auctioneerId
            existing.highestBid = auction.highestBid
            existing.highestBidder = highestBidder
            existing.passedPlayersJson = passedJson
            existing.timerEndTime = auction.timerEndTime
            existing.buyerPlayerId = auction.buyerId
            existing.sellerPlayerId = auction.sellerId
            auctionStateRepository.save(existing)
        }
    }

    private fun syncTradeState(
        game: GameEntity,
        playerEntities: Map<String, GamePlayerEntity>,
        state: GameState,
    ) {
        val trade = state.tradeState
        val existing = tradeStateRepository.findById(game.id).orElse(null)

        if (trade == null) {
            if (existing != null) tradeStateRepository.deleteById(game.id)
            return
        }

        val challenger =
            playerEntities[trade.initiatorId]
                ?: error("Trade initiator ${trade.initiatorId} not persisted")
        val defender =
            playerEntities[trade.targetId]
                ?: error("Trade defender ${trade.targetId} not persisted")

        val challengerCards =
            resolveMoneyCards(
                state = state,
                playerId = trade.initiatorId,
                cardIds = trade.offeredMoneyCardIds,
                selectedCards = trade.offeredMoneyCards,
            )
        val defenderCards =
            resolveMoneyCards(
                state = state,
                playerId = trade.targetId,
                cardIds = trade.counterOfferedMoneyCardIds,
                selectedCards = trade.counterOfferedMoneyCards,
            )
        val challengerValues = challengerCards.map { it.value }
        val defenderValues = defenderCards.map { it.value }

        if (existing == null) {
            tradeStateRepository.save(
                TradeStateEntity(
                    game = game,
                    challenger = challenger,
                    defender = defender,
                    animalType = trade.requestedAnimalType,
                    challengerOfferJson = GameStateMapper.encodeIntList(challengerValues),
                    defenderOfferJson = GameStateMapper.encodeIntList(defenderValues),
                    animalCardsJson = GameStateMapper.encodeAnimalCards(trade.animalCards.toList()),
                    challengerOfferCardsJson = GameStateMapper.encodeMoneyCards(challengerCards),
                    defenderOfferCardsJson = GameStateMapper.encodeMoneyCards(defenderCards),
                    winnerPlayerId = trade.winnerId,
                    isResolved = trade.isResolved,
                ),
            )
        } else {
            existing.challenger = challenger
            existing.defender = defender
            existing.animalType = trade.requestedAnimalType
            existing.challengerOfferJson = GameStateMapper.encodeIntList(challengerValues)
            existing.defenderOfferJson = GameStateMapper.encodeIntList(defenderValues)
            existing.animalCardsJson = GameStateMapper.encodeAnimalCards(trade.animalCards.toList())
            existing.challengerOfferCardsJson = GameStateMapper.encodeMoneyCards(challengerCards)
            existing.defenderOfferCardsJson = GameStateMapper.encodeMoneyCards(defenderCards)
            existing.winnerPlayerId = trade.winnerId
            existing.isResolved = trade.isResolved
            tradeStateRepository.save(existing)
        }
    }

    private fun resolveMoneyCards(
        state: GameState,
        playerId: String,
        cardIds: Set<String>,
        selectedCards: Set<MoneyCard>?,
    ): List<MoneyCard> =
        selectedCards?.toList()
            ?: state.players
                .firstOrNull { it.id == playerId }
                ?.moneyCards
                ?.filter { it.id in cardIds }
            ?: emptyList()

    private fun GamePlayerEntity.persistedPlayerId(): String =
        playerId ?: user.passwordHash.ifBlank { user.username }
}

sealed class ReconnectTokenMutationResult {
    data class Success(
        val state: GameState,
    ) : ReconnectTokenMutationResult()

    object InvalidToken : ReconnectTokenMutationResult()
}
