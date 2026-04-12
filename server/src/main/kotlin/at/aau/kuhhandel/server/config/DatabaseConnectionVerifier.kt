package at.aau.kuhhandel.server.config

import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component
import java.sql.Connection
import java.sql.DriverManager

fun interface JdbcConnectionFactory {
    fun open(
        url: String,
        username: String,
        password: String,
    ): Connection
}

@Component
class DriverManagerJdbcConnectionFactory : JdbcConnectionFactory {
    override fun open(
        url: String,
        username: String,
        password: String,
    ): Connection = DriverManager.getConnection(url, username, password)
}

@Component
@Profile("staging", "production")
class DatabaseConnectionVerifier(
    @Value("\${spring.datasource.url}") private val datasourceUrl: String,
    @Value("\${spring.datasource.username}") private val datasourceUsername: String,
    @Value("\${spring.datasource.password}") private val datasourcePassword: String,
    private val connectionFactory: JdbcConnectionFactory,
) : ApplicationRunner {
    private val logger = LoggerFactory.getLogger(javaClass)

    override fun run(args: ApplicationArguments) {
        connectionFactory
            .open(
                datasourceUrl,
                datasourceUsername,
                datasourcePassword,
            ).use { connection ->
                check(connection.isValid(2)) {
                    "Database connection was established but did not validate successfully."
                }
            }

        logger.info("Successfully connected to the configured PostgreSQL database.")
    }
}
