package at.aau.kuhhandel.server.persistence

import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.containers.PostgreSQLContainer

// abstrakte Test-Basis: startet per Testcontainers eine ECHTE Postgres-DB und reicht deren Zugangsdaten
// an Spring weiter. Subklassen testen so gegen echtes Postgres (statt H2).
abstract class PostgresDataJpaTest {
    companion object {
        @JvmStatic
        private val postgres: PostgreSQLContainer<Nothing> =
            PostgreSQLContainer<Nothing>("postgres:16-alpine").apply {
                withDatabaseName("kuhhandel_test")
                withUsername("kuhhandel")
                withPassword("kuhhandel")
            }

        @DynamicPropertySource
        @JvmStatic
        // füllt zur Laufzeit Springs Datasource-Properties mit URL/User/Passwort des Containers
        // (+ ddl-auto create-drop, Postgres-Dialekt) - @DynamicPropertySource, weil die URL erst beim Start feststeht.
        fun postgresProperties(registry: DynamicPropertyRegistry) {
            startPostgres()

            registry.add("spring.datasource.url", postgres::getJdbcUrl)
            registry.add("spring.datasource.username", postgres::getUsername)
            registry.add("spring.datasource.password", postgres::getPassword)
            registry.add("spring.datasource.driver-class-name") { "org.postgresql.Driver" }
            registry.add("spring.jpa.hibernate.ddl-auto") { "create-drop" }
            registry.add("spring.jpa.properties.hibernate.dialect") {
                "org.hibernate.dialect.PostgreSQLDialect"
            }
        }

        @Synchronized
        // startet den Container nur einmal (synchronized + idempotent) - wird von allen Tests geteilt.
        private fun startPostgres() {
            if (!postgres.isRunning) {
                postgres.start()
            }
        }
    }
}
