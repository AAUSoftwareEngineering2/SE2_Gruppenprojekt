package at.aau.kuhhandel.server.config

import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component
import java.sql.Connection
import java.sql.DriverManager

// fun interface = funktionales Interface (genau EINE Methode) -> kann auch als Lambda übergeben werden.
// Gibt es nur, damit man im Test eine Fake-Verbindung statt der echten DB reingeben kann.
fun interface JdbcConnectionFactory {
    fun open(
        url: String,
        username: String,
        password: String,
    ): Connection
}

// @Component = Spring-Bean (wird automatisch erzeugt und injiziert). Die echte Umsetzung:
// öffnet per JDBC eine echte DB-Verbindung (DriverManager.getConnection).
@Component
class DriverManagerJdbcConnectionFactory : JdbcConnectionFactory {
    override fun open(
        url: String,
        username: String,
        password: String,
    ): Connection = DriverManager.getConnection(url, username, password)
}

// @Component = Spring-Bean. @Profile = diese Bean existiert NUR, wenn das aktive Profil "staging"
// oder "production" ist (lokal mit H2 läuft sie also gar nicht).
// Die @Value("${...}")-Parameter unten holen DB-URL/User/Passwort aus der Config (application.yml/Env).
@Component
@Profile("staging", "production")
class DatabaseConnectionVerifier(
    @Value("\${spring.datasource.url}") private val datasourceUrl: String,
    @Value("\${spring.datasource.username}") private val datasourceUsername: String,
    @Value("\${spring.datasource.password}") private val datasourcePassword: String,
    private val connectionFactory: JdbcConnectionFactory,
) : ApplicationRunner {
    private val logger = LoggerFactory.getLogger(javaClass)

    // ApplicationRunner -> diese run()-Methode wird EINMAL direkt nach dem App-Start ausgeführt.
    override fun run(args: ApplicationArguments) {
        // H2 is on the runtime classpath for the local multi-pod script. Refuse to start on
        // staging/production if the datasource is not Postgres, so a missing or misconfigured
        // SPRING_DATASOURCE_URL can never silently fall back to an embedded H2 (data loss risk).
        // require = muss zutreffen, sonst Abbruch beim Start: die DB-URL MUSS Postgres sein
        // (sonst stilles Zurückfallen auf eingebettete H2 -> Datenverlust).
        require(datasourceUrl.startsWith(POSTGRES_URL_PREFIX)) {
            "staging/production requires a PostgreSQL datasource, but spring.datasource.url was " +
                "'$datasourceUrl'. Refusing to start on a non-Postgres database."
        }

        connectionFactory
            .open(
                datasourceUrl,
                datasourceUsername,
                datasourcePassword,
            ).use { connection ->
                // .use schließt die Connection danach automatisch (try-with-resources). check =
                // Zustand prüfen: isValid(2) testet die Verbindung mit 2s Timeout; sonst Exception.
                check(connection.isValid(2)) {
                    "Database connection was established but did not validate successfully."
                }
            }

        logger.info("Successfully connected to the configured PostgreSQL database.")
    }

    private companion object {
        const val POSTGRES_URL_PREFIX = "jdbc:postgresql:"
    }
}
