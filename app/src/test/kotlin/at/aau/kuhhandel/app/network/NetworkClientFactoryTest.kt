package at.aau.kuhhandel.app.network

import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test

class NetworkClientFactoryTest {
    @Test
    fun `create returns a client`() {
        val client = NetworkClientFactory.create()

        assertNotNull(client)

        client.close()
    }
}
