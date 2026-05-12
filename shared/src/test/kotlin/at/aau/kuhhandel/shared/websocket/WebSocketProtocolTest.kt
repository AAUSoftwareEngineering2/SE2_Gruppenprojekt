package at.aau.kuhhandel.shared.websocket

import at.aau.kuhhandel.shared.model.GameState
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
    fun `CreateGamePayload defaults are correct`() {
        val payload = CreateGamePayload()

        assertNull(payload.playerName)
    }

    @Test
    fun `CreateGamePayload round-trips`() {
        val payload = CreateGamePayload(playerName = "John")

        val encoded = json.encodeToString(CreateGamePayload.serializer(), payload)
        val decoded = json.decodeFromString(CreateGamePayload.serializer(), encoded)

        assertEquals(payload, decoded)
        assertEquals(payload.hashCode(), decoded.hashCode())
        assertEquals(payload.toString(), decoded.toString())
        assertEquals(payload, payload.copy())
    }

    @Test
    fun `GameCreatedPayload round-trips`() {
        // re-check this!
        val payload = GameCreatedPayload(gameId = "Id", playerId = "P1", state = GameState())

        val encoded = json.encodeToString(GameCreatedPayload.serializer(), payload)
        val decoded = json.decodeFromString(GameCreatedPayload.serializer(), encoded)

        assertEquals(payload, decoded)
        assertEquals(payload.hashCode(), decoded.hashCode())
        assertEquals(payload.toString(), decoded.toString())
        assertEquals(payload, payload.copy())
    }

    @Test
    fun `GameStatePayload round-trips`() {
        val payload = GameStatePayload(state = GameState())

        val encoded = json.encodeToString(GameStatePayload.serializer(), payload)
        val decoded = json.decodeFromString(GameStatePayload.serializer(), encoded)

        assertEquals(payload, decoded)
        assertEquals(payload.hashCode(), decoded.hashCode())
        assertEquals(payload.toString(), decoded.toString())
        assertEquals(payload, payload.copy())
    }

    @Test
    fun `JoinGamePayload round-trips`() {
        val payload = JoinGamePayload(gameId = "game-1", playerName = "Player 1")

        val encoded = json.encodeToString(JoinGamePayload.serializer(), payload)
        val decoded = json.decodeFromString(JoinGamePayload.serializer(), encoded)

        assertEquals(payload, decoded)
    }

    @Test
    fun `ErrorPayload round-trips`() {
        val payload = ErrorPayload(message = "Something went wrong")

        val encoded = json.encodeToString(ErrorPayload.serializer(), payload)
        val decoded = json.decodeFromString(ErrorPayload.serializer(), encoded)

        assertEquals(payload, decoded)
        assertEquals(payload.hashCode(), decoded.hashCode())
        assertEquals(payload.toString(), decoded.toString())
        assertEquals(payload, payload.copy())
    }

    @Test
    fun `InitiateTradePayload round-trips and exposes its fields`() {
        val payload =
            // re-check this!
            InitiateTradePayload(challengedPlayerId = "player-2", moneyCardIds = emptyList())

        assertEquals("player-2", payload.challengedPlayerId)

        val encoded = json.encodeToString(InitiateTradePayload.serializer(), payload)
        val decoded = json.decodeFromString(InitiateTradePayload.serializer(), encoded)

        assertEquals(payload, decoded)
        assertEquals(payload.hashCode(), decoded.hashCode())
        assertEquals(payload.toString(), decoded.toString())
        assertEquals(payload, payload.copy())
    }

    @Test
    fun `OfferTradePayload round-trips and exposes its fields`() {
        val payload = OfferTradePayload(moneyCardIds = listOf("m-10", "m-50"))

        assertEquals(listOf("m-10", "m-50"), payload.moneyCardIds)

        val encoded = json.encodeToString(OfferTradePayload.serializer(), payload)
        val decoded = json.decodeFromString(OfferTradePayload.serializer(), encoded)

        assertEquals(payload, decoded)
        assertEquals(payload.hashCode(), decoded.hashCode())
        assertEquals(payload.toString(), decoded.toString())
        assertEquals(payload, payload.copy())
    }

    @Test
    fun `RespondToTradePayload round-trips and exposes its fields`() {
        val payload =
            RespondToTradePayload(
                respondingPlayerId = "player-2",
                accepted = true,
                counterOfferedMoneyCardIds = emptyList(), // re-check this!
            )

        assertEquals("player-2", payload.respondingPlayerId)
        assertEquals(true, payload.accepted)

        val encoded = json.encodeToString(RespondToTradePayload.serializer(), payload)
        val decoded = json.decodeFromString(RespondToTradePayload.serializer(), encoded)

        assertEquals(payload, decoded)
        assertEquals(payload.hashCode(), decoded.hashCode())
        assertEquals(payload.toString(), decoded.toString())
        assertEquals(payload, payload.copy())
    }

    @Test
    fun `PlaceBidPayload round-trips and exposes its fields`() {
        val payload = PlaceBidPayload(amount = 100)
        assertEquals(100, payload.amount)

        val encoded = json.encodeToString(PlaceBidPayload.serializer(), payload)
        val decoded = json.decodeFromString(PlaceBidPayload.serializer(), encoded)

        assertEquals(payload, decoded)
        assertEquals(payload.hashCode(), decoded.hashCode())
        assertEquals(payload.toString(), decoded.toString())
    }

    @Test
    fun `AuctionBuyBackPayload round-trips and exposes its fields`() {
        val payload = AuctionBuyBackPayload(buyBack = true)
        assertEquals(true, payload.buyBack)

        val encoded = json.encodeToString(AuctionBuyBackPayload.serializer(), payload)
        val decoded = json.decodeFromString(AuctionBuyBackPayload.serializer(), encoded)

        assertEquals(payload, decoded)
        assertEquals(payload.hashCode(), decoded.hashCode())
        assertEquals(payload.toString(), decoded.toString())
        assertEquals(payload, payload.copy())
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
}
