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
import at.aau.kuhhandel.shared.model.PlayerState
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

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
    @Transactional
    fun saveGameState(
        gameId: String,
        state: GameState,
    ) {
        val gameKey = gameId.toLongOrNull() ?: error("Game id must be numeric, was '$gameId'")
        val game = upsertGame(gameKey, state)
        val playerEntities = syncPlayers(game, state.players)
        syncDeck(game, state)
        syncPlayerInventories(playerEntities, state.players)
        syncAuctionState(game, playerEntities, state)
        syncTradeState(game, playerEntities, state)
        game.activePlayer =
            state.players.getOrNull(state.currentPlayerIndex)?.let { active ->
                playerEntities[active.id]?.user
            }
        gameRepository.save(game)
    }

    @Transactional(readOnly = true)
    fun loadGameState(gameId: String): GameState? {
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

        return GameStateMapper.toGameState(
            game = game,
            players = players,
            animalsByPlayer = animalsByPlayer,
            moneyByPlayer = moneyByPlayer,
            deck = deck,
            auction = auction,
            trade = trade,
        )
    }

    @Transactional
    fun deleteGame(gameId: String) {
        val gameKey = gameId.toLongOrNull() ?: return
        val game = gameRepository.findById(gameKey).orElse(null) ?: return
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
    ): GameEntity {
        val existing = gameRepository.findById(gameKey).orElse(null)
        return if (existing == null) {
            gameRepository.save(
                GameEntity(
                    id = gameKey,
                    status = GameStateMapper.toGameStatus(state.phase),
                    roundNumber = state.roundNumber,
                ),
            )
        } else {
            existing.status = GameStateMapper.toGameStatus(state.phase)
            existing.roundNumber = state.roundNumber
            existing
        }
    }

    private fun syncPlayers(
        game: GameEntity,
        players: List<PlayerState>,
    ): Map<String, GamePlayerEntity> {
        val existing = gamePlayerRepository.findByGameOrderBySeatOrderAsc(game)
        val existingByUsername = existing.associateBy { it.user.username }
        val incomingUsernames = players.map { it.id }.toSet()

        // Drop players no longer in the game, including their inventories.
        existing
            .filter { it.user.username !in incomingUsernames }
            .forEach { stale ->
                playerMoneyRepository.deleteByPlayer(stale)
                playerAnimalRepository.deleteByPlayer(stale)
                gamePlayerRepository.delete(stale)
            }

        return players
            .mapIndexed { index, player ->
                val playerEntity = existingByUsername[player.id]
                val user = upsertUser(player.id, player.name)
                val saved =
                    if (playerEntity == null) {
                        gamePlayerRepository.save(
                            GamePlayerEntity(
                                game = game,
                                user = user,
                                seatOrder = index,
                            ),
                        )
                    } else {
                        playerEntity.seatOrder = index
                        playerEntity
                    }
                player.id to saved
            }.toMap()
    }

    private fun upsertUser(
        username: String,
        @Suppress("UNUSED_PARAMETER") displayName: String,
    ): UserEntity =
        userRepository.findByUsername(username)
            ?: userRepository.save(UserEntity(username = username, passwordHash = ""))

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
        players: List<PlayerState>,
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
        val existing = auctionStateRepository.findById(requireNotNull(game.id)).orElse(null)

        if (auction == null) {
            if (existing != null) auctionStateRepository.deleteById(requireNotNull(game.id))
            return
        }

        val passedJson = GameStateMapper.encodeStringList(emptyList())
        val highestBidder =
            auction.highestBidderId?.let { bidderId -> playerEntities[bidderId] }

        if (existing == null) {
            auctionStateRepository.save(
                AuctionStateEntity(
                    game = game,
                    currentAnimal = auction.auctionCard.type,
                    highestBid = auction.highestBid,
                    highestBidder = highestBidder,
                    passedPlayersJson = passedJson,
                ),
            )
        } else {
            existing.currentAnimal = auction.auctionCard.type
            existing.highestBid = auction.highestBid
            existing.highestBidder = highestBidder
            existing.passedPlayersJson = passedJson
            auctionStateRepository.save(existing)
        }
    }

    private fun syncTradeState(
        game: GameEntity,
        playerEntities: Map<String, GamePlayerEntity>,
        state: GameState,
    ) {
        val trade = state.tradeState
        val existing = tradeStateRepository.findById(requireNotNull(game.id)).orElse(null)

        if (trade == null) {
            if (existing != null) tradeStateRepository.deleteById(requireNotNull(game.id))
            return
        }

        val challenger =
            playerEntities[trade.initiatingPlayerId]
                ?: error("Trade initiator ${trade.initiatingPlayerId} not persisted")
        val defender =
            playerEntities[trade.challengedPlayerId]
                ?: error("Trade defender ${trade.challengedPlayerId} not persisted")

        val challengerValues =
            state.players
                .firstOrNull { it.id == trade.initiatingPlayerId }
                ?.moneyCards
                ?.filter { it.id in trade.offeredMoneyCardIds }
                ?.map { it.value }
                ?: emptyList()
        val defenderValues =
            state.players
                .firstOrNull { it.id == trade.challengedPlayerId }
                ?.moneyCards
                ?.filter { it.id in trade.counterOfferedMoneyCardIds }
                ?.map { it.value }
                ?: emptyList()

        if (existing == null) {
            tradeStateRepository.save(
                TradeStateEntity(
                    game = game,
                    challenger = challenger,
                    defender = defender,
                    animalType = trade.requestedAnimalType,
                    challengerOfferJson = GameStateMapper.encodeIntList(challengerValues),
                    defenderOfferJson = GameStateMapper.encodeIntList(defenderValues),
                ),
            )
        } else {
            existing.challenger = challenger
            existing.defender = defender
            existing.animalType = trade.requestedAnimalType
            existing.challengerOfferJson = GameStateMapper.encodeIntList(challengerValues)
            existing.defenderOfferJson = GameStateMapper.encodeIntList(defenderValues)
            tradeStateRepository.save(existing)
        }
    }
}
