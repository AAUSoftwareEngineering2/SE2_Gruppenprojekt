package at.aau.kuhhandel.shared.websocket

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
        assertNull(envelope.matchId)
        assertNull(envelope.playerId)
        assertNull(envelope.payload)
    }

    @Test
    fun `WebSocketEnvelope round-trips`() {
        val payload = CreateGamePayload(playerName = "John")
        val envelope =
            WebSocketEnvelope(
                type = WebSocketType.CREATE_GAME,
                requestId = "request-1",
                matchId = "match-1",
                playerId = "player-1",
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
}
