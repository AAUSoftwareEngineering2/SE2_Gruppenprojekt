package at.aau.kuhhandel.server.persistence.mapper

import at.aau.kuhhandel.server.persistence.entity.AuctionStateEntity
import at.aau.kuhhandel.server.persistence.entity.DeckCardEntity
import at.aau.kuhhandel.server.persistence.entity.GameEntity
import at.aau.kuhhandel.server.persistence.entity.GamePlayerEntity
import at.aau.kuhhandel.server.persistence.entity.GameStatus
import at.aau.kuhhandel.server.persistence.entity.PlayerAnimalEntity
import at.aau.kuhhandel.server.persistence.entity.PlayerMoneyEntity
import at.aau.kuhhandel.server.persistence.entity.TradeStateEntity
import at.aau.kuhhandel.shared.enums.GamePhase
import at.aau.kuhhandel.shared.enums.TradeStep
import at.aau.kuhhandel.shared.model.AnimalCard
import at.aau.kuhhandel.shared.model.AnimalDeck
import at.aau.kuhhandel.shared.model.AuctionState
import at.aau.kuhhandel.shared.model.GameState
import at.aau.kuhhandel.shared.model.MoneyCard
import at.aau.kuhhandel.shared.model.PlayerState
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

    fun toGameStatus(phase: GamePhase): GameStatus =
        when (phase) {
            GamePhase.AUCTION -> GameStatus.AUCTION
            GamePhase.TRADE -> GameStatus.TRADE
            GamePhase.FINISHED -> GameStatus.FINISHED
            GamePhase.NOT_STARTED, GamePhase.PLAYER_TURN, GamePhase.ROUND_END -> GameStatus.LOBBY
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

    fun decodeStringList(payload: String?): List<String> =
        if (payload.isNullOrBlank()) {
            emptyList()
        } else {
            json.decodeFromString(
                stringListSerializer,
                payload,
            )
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

        val playerStates =
            sortedPlayers.map { player ->
                val playerKey = requireNotNull(player.id) { "GamePlayerEntity must be persisted" }
                PlayerState(
                    id = player.user.username,
                    name = player.user.username,
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
                )
            }

        val activeUsername = game.activePlayer?.username
        val currentPlayerIndex =
            playerStates.indexOfFirst { it.id == activeUsername }.coerceAtLeast(0)

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
                    playerStates,
                )
            }
        val tradeDto = trade?.let { entity -> toTradeState(entity, sortedPlayers, playerStates) }

        val phase = toGamePhase(game.status, animalDeck, playerStates)

        val faceUpCard =
            game.faceUpAnimalType?.let { animalType ->
                AnimalCard(id = "faceup-$gameIdString", type = animalType)
            }

        return GameState(
            phase = phase,
            roundNumber = game.roundNumber,
            deck = animalDeck,
            currentFaceUpCard = faceUpCard,
            currentPlayerIndex = currentPlayerIndex,
            players = playerStates,
            auctionState = auctionDto,
            tradeState = tradeDto,
        )
    }

    private fun toGamePhase(
        status: GameStatus,
        deck: AnimalDeck,
        players: List<PlayerState>,
    ): GamePhase =
        when (status) {
            GameStatus.AUCTION -> GamePhase.AUCTION
            GameStatus.TRADE -> GamePhase.TRADE
            GameStatus.FINISHED -> GamePhase.FINISHED
            GameStatus.LOBBY ->
                if (players.isEmpty() ||
                    deck.isEmpty()
                ) {
                    GamePhase.NOT_STARTED
                } else {
                    GamePhase.PLAYER_TURN
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
        playerStates: List<PlayerState>,
    ): AuctionState {
        val auctioneerUsername =
            entity.game.activePlayer?.username
                ?: playerStates.firstOrNull()?.id
                ?: ""
        val highestBidderUsername =
            entity.highestBidder?.let { bidder -> findUsernameForPlayer(bidder, players) }

        return AuctionState(
            auctionCard = AnimalCard(id = "auction-$gameId", type = entity.currentAnimal),
            auctioneerId = auctioneerUsername,
            highestBid = entity.highestBid,
            highestBidderId = highestBidderUsername,
            isClosed = entity.isClosed,
            timerEndTime = entity.timerEndTime,
        )
    }

    private fun toTradeState(
        entity: TradeStateEntity,
        players: List<GamePlayerEntity>,
        playerStates: List<PlayerState>,
    ): TradeState {
        val challengerUsername = findUsernameForPlayer(entity.challenger, players) ?: ""
        val defenderUsername = findUsernameForPlayer(entity.defender, players) ?: ""
        val challengerOfferValues = decodeIntList(entity.challengerOfferJson)
        val defenderOfferValues = decodeIntList(entity.defenderOfferJson)

        val challengerMoneyIds =
            synthesizeMoneyIds(challengerUsername, challengerOfferValues, prefix = "ch")
        val defenderMoneyIds =
            synthesizeMoneyIds(defenderUsername, defenderOfferValues, prefix = "de")

        return TradeState(
            initiatingPlayerId = challengerUsername,
            challengedPlayerId = defenderUsername,
            requestedAnimalType = entity.animalType,
            step = TradeStep.WAITING_FOR_RESPONSE,
            offeredMoney = challengerOfferValues.sum(),
            offeredMoneyCardIds = challengerMoneyIds,
            offeredMoneyCardCount = challengerOfferValues.size,
            counterOfferedMoney =
                if (defenderOfferValues.isEmpty()) {
                    null
                } else {
                    defenderOfferValues
                        .sum()
                },
            counterOfferedMoneyCardIds = defenderMoneyIds,
            counterOfferedMoneyCardCount = defenderOfferValues.size,
            isResolved = false,
        )
    }

    private fun synthesizeMoneyIds(
        username: String,
        values: List<Int>,
        prefix: String,
    ): List<String> = values.mapIndexed { i, value -> "$prefix-$username-$value-$i" }

    private fun findUsernameForPlayer(
        player: GamePlayerEntity,
        all: List<GamePlayerEntity>,
    ): String? = all.firstOrNull { it.id == player.id }?.user?.username
}
