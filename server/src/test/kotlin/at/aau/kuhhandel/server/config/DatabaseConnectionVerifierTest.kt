package at.aau.kuhhandel.server.config

import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.verifyNoInteractions
import org.mockito.Mockito.`when`
import org.springframework.boot.DefaultApplicationArguments
import java.sql.Connection
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class DatabaseConnectionVerifierTest {
    private val applicationArguments = DefaultApplicationArguments()
    private val postgresUrl = "jdbc:postgresql://localhost:5432/kuhhandel"

    @Test
    // testet: dass bei gesunder DB-Verbindung isValid(2) geprueft und die Verbindung danach geschlossen wird.
    fun `run validates and closes connection when database connection is healthy`() {
        val connection = mock(Connection::class.java)
        `when`(connection.isValid(2)).thenReturn(true)

        val verifier =
            DatabaseConnectionVerifier(
                datasourceUrl = postgresUrl,
                datasourceUsername = "user",
                datasourcePassword = "secret",
                connectionFactory =
                    JdbcConnectionFactory { url, username, password ->
                        assertEquals(postgresUrl, url)
                        assertEquals("user", username)
                        assertEquals("secret", password)
                        connection
                    },
            )

        verifier.run(applicationArguments)

        verify(connection).isValid(2)
        verify(connection).close()
    }

    @Test
    // testet: dass bei fehlgeschlagener Validierung eine IllegalStateException geworfen und die Verbindung geschlossen wird.
    fun `run throws when database connection validation fails`() {
        val connection = mock(Connection::class.java)
        `when`(connection.isValid(2)).thenReturn(false)

        val verifier =
            DatabaseConnectionVerifier(
                datasourceUrl = postgresUrl,
                datasourceUsername = "user",
                datasourcePassword = "secret",
                connectionFactory =
                    JdbcConnectionFactory { _, _, _ -> connection },
            )

        val exception =
            assertFailsWith<IllegalStateException> {
                verifier.run(applicationArguments)
            }

        assertEquals(
            "Database connection was established but did not validate successfully.",
            exception.message,
        )
        verify(connection).isValid(2)
        verify(connection).close()
    }

    @Test
    // testet: dass bei einer Nicht-Postgres-Datenquelle sofort (vor jedem Verbindungsaufbau) eine IllegalArgumentException geworfen wird.
    fun `run refuses to start on a non-Postgres datasource and never opens a connection`() {
        val connectionFactory = mock(JdbcConnectionFactory::class.java)

        val verifier =
            DatabaseConnectionVerifier(
                datasourceUrl = "jdbc:h2:mem:accidental",
                datasourceUsername = "sa",
                datasourcePassword = "",
                connectionFactory = connectionFactory,
            )

        val exception =
            assertFailsWith<IllegalArgumentException> {
                verifier.run(applicationArguments)
            }

        assertEquals(true, exception.message?.contains("PostgreSQL"))
        // Fails fast before touching the database.
        verifyNoInteractions(connectionFactory)
    }
}
