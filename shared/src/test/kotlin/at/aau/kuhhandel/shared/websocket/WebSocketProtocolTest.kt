package at.aau.kuhhandel.shared.websocket

import at.aau.kuhhandel.shared.enums.AnimalType
import at.aau.kuhhandel.shared.enums.GamePhase
import at.aau.kuhhandel.shared.model.GameState
import at.aau.kuhhandel.shared.model.GameStateView
import at.aau.kuhhandel.shared.model.Player
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class WebSocketProtocolTest {
    private val json = WebSocketJson.json

    // WebSocketType tests

    @Test
    fun `WebSocketType serializes and deserializes`() {
        val encoded = json.encodeToString(WebSocketType.serializer(), WebSocketType.CREATE_GAME)
        val decoded = json.decodeFromString(WebSocketType.serializer(), encoded)

        assertEquals(WebSocketType.CREATE_GAME, decoded)
    }

    // WebSocketEnvelope tests

    @Test
    fun `WebSocketEnvelope defaults are correct`() {
        val envelope = WebSocketEnvelope(type = WebSocketType.CREATE_GAME)

        assertEquals(WebSocketType.CREATE_GAME, envelope.type)
        assertNull(envelope.requestId)
        assertNull(envelope.payload)
    }

    @Test
    fun `WebSocketEnvelope round-trips`() {
        val payload = CreateGamePayload(playerName = "John")
        val envelope =
            WebSocketEnvelope(
                type = WebSocketType.CREATE_GAME,
                requestId = "request-1",
                payload = json.encodeToJsonElement(CreateGamePayload.serializer(), payload),
            )

        val encoded = json.encodeToString(WebSocketEnvelope.serializer(), envelope)
        val decoded = json.decodeFromString(WebSocketEnvelope.serializer(), encoded)

        assertEquals(envelope, decoded)
    }

    // WebSocketPayloads tests

    @Test
    fun `CreateGamePayload round-trips`() {
        val payload = CreateGamePayload(playerName = "John")

        val encoded = json.encodeToString(CreateGamePayload.serializer(), payload)
        val decoded = json.decodeFromString(CreateGamePayload.serializer(), encoded)

        assertEquals(payload, decoded)
    }

    @Test
    fun `GameCreatedPayload round-trips`() {
        val payload =
            GameCreatedPayload(
                gameId = "Id",
                playerId = "P1",
                reconnectToken = "token-1",
                state = GameState(),
                stateView = testGameStateView(),
            )

        val encoded = json.encodeToString(GameCreatedPayload.serializer(), payload)
        val decoded = json.decodeFromString(GameCreatedPayload.serializer(), encoded)

        assertEquals(payload, decoded)
    }

    @Test
    fun `GameStatePayload round-trips`() {
        val payload = GameStatePayload(state = GameState(), stateView = testGameStateView())

        val encoded = json.encodeToString(GameStatePayload.serializer(), payload)
        val decoded = json.decodeFromString(GameStatePayload.serializer(), encoded)

        assertEquals(payload, decoded)
    }

    @Test
    fun `JoinGamePayload round-trips`() {
        val payload = JoinGamePayload(gameId = "game-1", playerName = "Player 1")

        val encoded = json.encodeToString(JoinGamePayload.serializer(), payload)
        val decoded = json.decodeFromString(JoinGamePayload.serializer(), encoded)

        assertEquals(payload, decoded)
    }

    @Test
    fun `GameJoinedPayload round-trips`() {
        val payload =
            GameJoinedPayload(
                playerId = "player-1",
                reconnectToken = "token-1",
                state = GameState(),
                stateView = testGameStateView(),
            )

        val encoded = json.encodeToString(GameJoinedPayload.serializer(), payload)
        val decoded = json.decodeFromString(GameJoinedPayload.serializer(), encoded)

        assertEquals(payload, decoded)
    }

    @Test
    fun `ReconnectPayload round-trips`() {
        val payload = ReconnectPayload(gameId = "game-1", playerId = "player-1", token = "token-1")

        val encoded = json.encodeToString(ReconnectPayload.serializer(), payload)
        val decoded = json.decodeFromString(ReconnectPayload.serializer(), encoded)

        assertEquals(payload, decoded)
    }

    @Test
    fun `SnapshotPayload round-trips`() {
        val payload =
            SnapshotPayload(
                reconnectToken = "token-1",
                state = GameState(),
                stateView = testGameStateView(),
            )

        val encoded = json.encodeToString(SnapshotPayload.serializer(), payload)
        val decoded = json.decodeFromString(SnapshotPayload.serializer(), encoded)

        assertEquals(payload, decoded)
    }

    @Test
    fun `PlaceBidPayload round-trips`() {
        val payload = PlaceBidPayload(amount = 100)

        val encoded = json.encodeToString(PlaceBidPayload.serializer(), payload)
        val decoded = json.decodeFromString(PlaceBidPayload.serializer(), encoded)

        assertEquals(payload, decoded)
    }

    @Test
    fun `ResolveAuctionPayload round-trips`() {
        val payload = ResolveAuctionPayload(buyBack = true)

        val encoded = json.encodeToString(ResolveAuctionPayload.serializer(), payload)
        val decoded = json.decodeFromString(ResolveAuctionPayload.serializer(), encoded)

        assertEquals(payload, decoded)
    }

    @Test
    fun `ChooseTradePayload round-trips`() {
        val payload =
            ChooseTradePayload(
                challengedPlayerId = "player-2",
                animalType = AnimalType.COW,
            )

        val encoded = json.encodeToString(ChooseTradePayload.serializer(), payload)
        val decoded = json.decodeFromString(ChooseTradePayload.serializer(), encoded)

        assertEquals(payload, decoded)
    }

    @Test
    fun `SubmitTradeMoneyPayload round-trips`() {
        val payload =
            SubmitTradeMoneyPayload(
                moneyCardIds = emptySet(),
            )

        val encoded = json.encodeToString(SubmitTradeMoneyPayload.serializer(), payload)
        val decoded = json.decodeFromString(SubmitTradeMoneyPayload.serializer(), encoded)

        assertEquals(payload, decoded)
    }

    @Test
    fun `RespondToTradePayload round-trips`() {
        val payload =
            RespondToTradePayload(
                moneyCardIds = emptySet(),
            )

        val encoded = json.encodeToString(RespondToTradePayload.serializer(), payload)
        val decoded = json.decodeFromString(RespondToTradePayload.serializer(), encoded)

        assertEquals(payload, decoded)
    }

    @Test
    fun `SpyPayload round-trips`() {
        val payload = SpyPayload(targetPlayerId = "player-1")

        val encoded = json.encodeToString(SpyPayload.serializer(), payload)
        val decoded = json.decodeFromString(SpyPayload.serializer(), encoded)

        assertEquals(payload, decoded)
    }

    @Test
    fun `ErrorPayload round-trips`() {
        val payload = ErrorPayload(message = "Something went wrong")

        val encoded = json.encodeToString(ErrorPayload.serializer(), payload)
        val decoded = json.decodeFromString(ErrorPayload.serializer(), encoded)

        assertEquals(payload, decoded)
    }

    // WebSocketJson tests

    @Test
    fun `WebSocketJson ignores unknown keys`() {
        val rawJson =
            """
            {
                "playerName": "John",
                "extraField": "shouldBeIgnored"
            }
            """.trimIndent()

        val decoded = json.decodeFromString(CreateGamePayload.serializer(), rawJson)

        assertEquals(CreateGamePayload(playerName = "John"), decoded)
    }

    @Test
    fun `null fields are omitted in JSON`() {
        val envelope = WebSocketEnvelope(type = WebSocketType.CREATE_GAME)

        val encoded = json.encodeToString(WebSocketEnvelope.serializer(), envelope)

        assert(!encoded.contains("requestId"))
        assert(!encoded.contains("matchId"))
        assert(!encoded.contains("playerId"))
        assert(!encoded.contains("payload"))
    }

    private fun testGameStateView() =
        GameStateView(
            phase = GamePhase.NOT_STARTED,
            timerEnd = null,
            localPlayer = Player("player-1", "Player 1"),
            opponents = emptyList(),
            hostPlayerId = "player-1",
            roundNumber = 0,
            currentPlayerId = null,
            deckSize = 5,
            auctionState = null,
            tradeState = null,
            alreadySpied = false,
            spyingTargetId = null,
            spyingTargetCards = null,
            localPlayerSpiedOn = false,
            spiedOnOpponentIds = emptyList(),
            lastEvent = null,
        )
}
