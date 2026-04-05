package com.example.server

import com.example.shared.ApiRoutes
import io.ktor.client.request.get
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import kotlin.test.assertEquals
import org.junit.Test

class ServerTest {

    @Test
    fun testConfigurePlugins() {
        testApplication {
            application { configurePlugins() }
            // Plugins load silently, so simply not throwing an exception
            // is a decent basic test for 0% coverage.
        }
    }

    @Test
    fun testConfigureRoutes() {
        testApplication {
            // 1. Load the plugins (needed for JSON serialization)
            application { configurePlugins() }

            // 2. Load the routes
            application { configureRoutes() }

            // 3. Make the request using the exact same constant the server uses
            val response = client.get(ApiRoutes.HEALTH)

            // 4. Verify it works
            assertEquals(HttpStatusCode.OK, response.status)
        }
    }
}
