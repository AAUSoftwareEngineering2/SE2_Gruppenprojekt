package at.aau.kuhhandel.server.config

import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.springframework.boot.DefaultApplicationArguments
import java.sql.Connection
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class DatabaseConnectionVerifierTest {
    private val applicationArguments = DefaultApplicationArguments()

    @Test
    fun `run validates and closes connection when database connection is healthy`() {
        val connection = mock(Connection::class.java)
        `when`(connection.isValid(2)).thenReturn(true)

        val verifier =
            DatabaseConnectionVerifier(
                datasourceUrl = "jdbc:testdb:healthy",
                datasourceUsername = "user",
                datasourcePassword = "secret",
                connectionFactory =
                    JdbcConnectionFactory { url, username, password ->
                        assertEquals("jdbc:testdb:healthy", url)
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
    fun `run throws when database connection validation fails`() {
        val connection = mock(Connection::class.java)
        `when`(connection.isValid(2)).thenReturn(false)

        val verifier =
            DatabaseConnectionVerifier(
                datasourceUrl = "jdbc:testdb:invalid",
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
}
