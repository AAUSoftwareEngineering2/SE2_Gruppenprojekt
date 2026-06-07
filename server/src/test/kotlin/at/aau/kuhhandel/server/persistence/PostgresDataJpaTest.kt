package at.aau.kuhhandel.server.persistence

import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.containers.PostgreSQLContainer

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
        private fun startPostgres() {
            if (!postgres.isRunning) {
                postgres.start()
            }
        }
    }
}
