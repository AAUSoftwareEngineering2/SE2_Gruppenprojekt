package at.aau.kuhhandel.server.persistence.mapper

import at.aau.kuhhandel.server.persistence.entity.AuctionStateEntity
import at.aau.kuhhandel.server.persistence.entity.DeckCardEntity
import at.aau.kuhhandel.server.persistence.entity.GameEntity
import at.aau.kuhhandel.server.persistence.entity.GamePlayerEntity
import at.aau.kuhhandel.server.persistence.entity.GameStatus
import at.aau.kuhhandel.server.persistence.entity.PlayerAnimalEntity
import at.aau.kuhhandel.server.persistence.entity.PlayerMoneyEntity
import at.aau.kuhhandel.server.persistence.entity.TradeStateEntity
import at.aau.kuhhandel.server.persistence.mapper.GameStateMapper.expandMoney
import at.aau.kuhhandel.shared.enums.GamePhase
import at.aau.kuhhandel.shared.model.AnimalCard
import at.aau.kuhhandel.shared.model.AnimalDeck
import at.aau.kuhhandel.shared.model.AuctionState
import at.aau.kuhhandel.shared.model.GameState
import at.aau.kuhhandel.shared.model.MoneyCard
import at.aau.kuhhandel.shared.model.Player
import at.aau.kuhhandel.shared.model.SpyAction
import at.aau.kuhhandel.shared.model.TradeState
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

/**
 * Bridges the persistence entity model (server-only) and the shared `@Serializable` DTOs.
 *
 * Lossy fields the snapshot intentionally does not preserve:
 *  - Individual money/animal card IDs (only aggregate counts are stored); synthetic IDs are
 *    generated on reload so the in-memory game logic (which keys off card IDs) keeps working.
 *  - The current trade step — restored as WAITING_FOR_RESPONSE.
 *  - The auctioneer's user id (derived from `games.active_player_id` on reload; relies on the
 *    invariant that the auctioneer is the active player when the auction starts).
 */
object GameStateMapper {
    private val json = Json { ignoreUnknownKeys = true }
    private val intListSerializer = ListSerializer(Int.serializer())
    private val stringListSerializer = ListSerializer(String.serializer())
    private val animalCardListSerializer = ListSerializer(AnimalCard.serializer())
    private val moneyCardListSerializer = ListSerializer(MoneyCard.serializer())
    private val spyActionListSerializer = ListSerializer(SpyAction.serializer())

    fun toGameStatus(phase: GamePhase): GameStatus =
        when (phase) {
            GamePhase.AUCTION_BIDDING,
            GamePhase.AUCTIONEER_DECISION,
            GamePhase.AUCTION_PAYMENT,
            GamePhase.AUCTION_RESULT,
            -> GameStatus.AUCTION

            GamePhase.TRADE_OFFER,
            GamePhase.TRADE_RESPONSE,
            GamePhase.TRADE_RESULT,
            -> GameStatus.TRADE

            GamePhase.FINISHED -> GameStatus.FINISHED
            GamePhase.NOT_STARTED, GamePhase.PLAYER_CHOICE -> GameStatus.LOBBY
        }

    fun encodeIntList(values: List<Int>): String = json.encodeToString(intListSerializer, values)

    fun decodeIntList(payload: String?): List<Int> =
        if (payload.isNullOrBlank()) {
            emptyList()
        } else {
            json.decodeFromString(
                intListSerializer,
                payload,
            )
        }

    fun encodeStringList(values: List<String>): String =
        json.encodeToString(stringListSerializer, values)

    fun encodeAnimalCards(values: List<AnimalCard>): String =
        json.encodeToString(animalCardListSerializer, values)

    fun encodeMoneyCards(values: List<MoneyCard>): String =
        json.encodeToString(moneyCardListSerializer, values)

    fun encodeSpies(values: Set<SpyAction>): String =
        json.encodeToString(spyActionListSerializer, values.toList())

    fun decodeSpies(payload: String?): Set<SpyAction> =
        if (payload.isNullOrBlank()) {
            emptySet()
        } else {
            json.decodeFromString(spyActionListSerializer, payload).toSet()
        }

    fun decodeStringList(payload: String?): List<String> =
        if (payload.isNullOrBlank()) {
            emptyList()
        } else {
            json.decodeFromString(
                stringListSerializer,
                payload,
            )
        }

    private fun decodeAnimalCards(payload: String?): List<AnimalCard>? =
        payload?.takeIf { it.isNotBlank() }?.let {
            json.decodeFromString(animalCardListSerializer, it)
        }

    private fun decodeMoneyCards(payload: String?): List<MoneyCard>? =
        payload?.takeIf { it.isNotBlank() }?.let {
            json.decodeFromString(moneyCardListSerializer, it)
        }

    fun toGameState(
        game: GameEntity,
        players: List<GamePlayerEntity>,
        animalsByPlayer: Map<Long, List<PlayerAnimalEntity>>,
        moneyByPlayer: Map<Long, List<PlayerMoneyEntity>>,
        deck: List<DeckCardEntity>,
        auction: AuctionStateEntity?,
        trade: TradeStateEntity?,
    ): GameState {
        val sortedPlayers = players.sortedBy { it.seatOrder }
        val gameIdString = game.id.toString()

        val domainPlayers =
            sortedPlayers.map { player ->
                val playerKey = requireNotNull(player.id) { "GamePlayerEntity must be persisted" }
                Player(
                    id = player.persistedPlayerId(),
                    name = player.persistedDisplayName(),
                    animals =
                        expandAnimals(
                            gameIdString,
                            playerKey,
                            animalsByPlayer[playerKey].orEmpty(),
                        ),
                    moneyCards =
                        expandMoney(
                            gameIdString,
                            playerKey,
                            moneyByPlayer[playerKey].orEmpty(),
                        ),
                    isConnected = player.isConnected,
                )
            }

        val activeUserId = game.activePlayer?.id
        val currentPlayerIndex =
            if (activeUserId == null) {
                -1
            } else {
                sortedPlayers.indexOfFirst { it.user.id == activeUserId }.let {
                    if (it ==
                        -1
                    ) {
                        0
                    } else {
                        it
                    }
                }
            }

        val animalDeck =
            AnimalDeck(
                cards =
                    deck
                        .sortedBy { it.drawOrder }
                        .mapIndexed {
                            index,
                            card,
                            ->
                            AnimalCard(id = "deck-$gameIdString-$index", type = card.animalType)
                        },
            )

        val auctionDto =
            auction?.let { entity ->
                toAuctionState(
                    gameIdString,
                    entity,
                    sortedPlayers,
                    domainPlayers,
                )
            }
        val phase = game.phase ?: toGamePhase(game.status, animalDeck, domainPlayers)
        val tradeDto =
            trade?.let { entity ->
                toTradeState(gameIdString, phase, entity, sortedPlayers)
            }

        val faceUpCard =
            game.faceUpAnimalType?.let { animalType ->
                AnimalCard(id = "faceup-$gameIdString", type = animalType)
            }

        return GameState(
            phase = phase,
            roundNumber = game.roundNumber,
            timerEnd = game.timerEnd,
            deck = animalDeck,
            currentFaceUpCard = faceUpCard,
            currentPlayerIndex = currentPlayerIndex,
            players = domainPlayers,
            auctionState = auctionDto,
            tradeState = tradeDto,
            hostPlayerId = game.hostPlayerId ?: domainPlayers.firstOrNull()?.id,
            activeSpies = decodeSpies(game.activeSpiesJson),
            spiedThisTurn = decodeStringList(game.spiedThisTurnJson).toSet(),
        )
    }

    private fun toGamePhase(
        status: GameStatus,
        deck: AnimalDeck,
        players: List<Player>,
    ): GamePhase =
        when (status) {
            // On restart mid-auction, return to bidding phase; the auction state is still present.
            GameStatus.AUCTION -> GamePhase.AUCTION_BIDDING
            // On restart mid-trade, return to offer phase; the trade state is still present.
            GameStatus.TRADE -> GamePhase.TRADE_OFFER
            GameStatus.FINISHED -> GamePhase.FINISHED
            GameStatus.LOBBY ->
                if (players.isEmpty() ||
                    deck.isEmpty()
                ) {
                    GamePhase.NOT_STARTED
                } else {
                    GamePhase.PLAYER_CHOICE
                }
        }

    private fun expandAnimals(
        gameId: String,
        playerId: Long,
        rows: List<PlayerAnimalEntity>,
    ): List<AnimalCard> =
        rows.flatMap { row ->
            (0 until row.amount).map { i ->
                AnimalCard(
                    id = "p$playerId-a-${row.animalType.name}-$i-$gameId",
                    type = row.animalType,
                )
            }
        }

    private fun expandMoney(
        gameId: String,
        playerId: Long,
        rows: List<PlayerMoneyEntity>,
    ): List<MoneyCard> =
        rows.flatMap { row ->
            (0 until row.amount).map { i ->
                MoneyCard(id = "p$playerId-m-${row.cardValue}-$i-$gameId", value = row.cardValue)
            }
        }

    private fun toAuctionState(
        gameId: String,
        entity: AuctionStateEntity,
        players: List<GamePlayerEntity>,
        domainPlayers: List<Player>,
    ): AuctionState {
        val auctioneerId =
            entity.auctioneerPlayerId
                ?: entity.game.activePlayer?.let { active ->
                    players.firstOrNull { it.user.id == active.id }?.persistedPlayerId()
                } ?: domainPlayers.firstOrNull()?.id ?: ""
        val highestBidderId =
            entity.highestBidder?.let { bidder -> findPlayerIdForPlayer(bidder, players) }

        return AuctionState(
            auctionCard = AnimalCard(id = "auction-$gameId", type = entity.currentAnimal),
            auctioneerId = auctioneerId,
            highestBid = entity.highestBid,
            highestBidderId = highestBidderId,
            timerEndTime = entity.timerEndTime,
            excludedPlayerIds = decodeStringList(entity.passedPlayersJson).toSet(),
            buyerId = entity.buyerPlayerId,
            sellerId = entity.sellerPlayerId,
        )
    }

    private fun toTradeState(
        gameId: String,
        phase: GamePhase,
        entity: TradeStateEntity,
        players: List<GamePlayerEntity>,
    ): TradeState {
        val challengerId = findPlayerIdForPlayer(entity.challenger, players) ?: ""
        val defenderId = findPlayerIdForPlayer(entity.defender, players) ?: ""
        val challengerEntityId = requireNotNull(entity.challenger.id)
        val defenderEntityId = requireNotNull(entity.defender.id)
        val challengerOfferValues = decodeIntList(entity.challengerOfferJson)
        val defenderOfferValues = decodeIntList(entity.defenderOfferJson)
        val animalCards = decodeAnimalCards(entity.animalCardsJson).orEmpty().toSet()

        val challengerMoneyIds =
            expandMoneyIds(gameId, challengerEntityId, challengerOfferValues)
        val defenderMoneyIds =
            expandMoneyIds(gameId, defenderEntityId, defenderOfferValues)
        val challengerCards =
            decodeMoneyCards(entity.challengerOfferCardsJson)
                ?: expandMoneyCards(challengerMoneyIds, challengerOfferValues)
        val defenderCards =
            decodeMoneyCards(entity.defenderOfferCardsJson)
                ?: expandMoneyCards(defenderMoneyIds, defenderOfferValues)
        val hasSubmittedOffer =
            phase != GamePhase.TRADE_OFFER ||
                challengerCards.isNotEmpty() ||
                hasNonEmptyJsonArray(entity.challengerOfferCardsJson)
        val hasSubmittedCounter =
            phase == GamePhase.TRADE_RESULT ||
                defenderCards.isNotEmpty() ||
                hasNonEmptyJsonArray(entity.defenderOfferCardsJson)

        return TradeState(
            initiatorId = challengerId,
            targetId = defenderId,
            requestedAnimalType = entity.animalType,
            animalCards = animalCards,
            offeredMoney = challengerCards.sumOf { it.value },
            offeredMoneyCardIds = challengerCards.mapTo(mutableSetOf()) { it.id },
            counterOfferedMoney =
                if (hasSubmittedCounter) defenderCards.sumOf { it.value } else null,
            counterOfferedMoneyCardIds = defenderCards.mapTo(mutableSetOf()) { it.id },
            isResolved = entity.isResolved == true,
            offeredMoneyCards = if (hasSubmittedOffer) challengerCards.toSet() else null,
            counterOfferedMoneyCards = if (hasSubmittedCounter) defenderCards.toSet() else null,
            winnerId = entity.winnerPlayerId,
        )
    }

    /**
     * Generates card IDs that match the formula used in [expandMoney], so that
     * `offeredMoneyCardIds` in a restored [TradeState] point to real cards in the player's hand.
     *
     * The DB stores only aggregate value counts (e.g. "two 10-chips"), so we reproduce the same
     * deterministic ID for index 0, 1, … within each denomination bucket.
     */
    private fun expandMoneyIds(
        gameId: String,
        playerId: Long,
        values: List<Int>,
    ): List<String> {
        val counterByValue = mutableMapOf<Int, Int>()
        return values.map { value ->
            val i = counterByValue.getOrDefault(value, 0)
            counterByValue[value] = i + 1
            "p$playerId-m-$value-$i-$gameId"
        }
    }

    private fun expandMoneyCards(
        ids: List<String>,
        values: List<Int>,
    ): List<MoneyCard> =
        values.mapIndexed { index, value ->
            MoneyCard(id = ids[index], value = value)
        }

    private fun hasNonEmptyJsonArray(payload: String?): Boolean =
        !payload.isNullOrBlank() && payload != "[]"

    private fun findPlayerIdForPlayer(
        player: GamePlayerEntity,
        all: List<GamePlayerEntity>,
    ): String? = all.firstOrNull { it.id == player.id }?.persistedPlayerId()

    private fun GamePlayerEntity.persistedPlayerId(): String =
        playerId ?: user.passwordHash.ifBlank { user.username }

    private fun GamePlayerEntity.persistedDisplayName(): String = displayName ?: user.username
}
