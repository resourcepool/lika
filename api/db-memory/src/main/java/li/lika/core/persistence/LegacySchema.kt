package li.lika.core.persistence

import jakarta.annotation.PostConstruct
import jakarta.persistence.EntityManagerFactory
import mu.KotlinLogging
import org.springframework.stereotype.Component
import javax.sql.DataSource

/**
 * Hibernate adds what is missing to the schema, but never removes anything: what must go is removed here.
 * Depends on the entity manager factory, to run once Hibernate has updated the schema.
 */
@Component
class LegacySchema(private val dataSource: DataSource, @Suppress("unused") private val entityManagerFactory: EntityManagerFactory) {

    private val logger = KotlinLogging.logger {}

    @PostConstruct
    fun drop() {
        // Lika < 0.17.0: one user API key per user. Created as a constraint or as an index, depending on the database.
        run("alter table api_keys drop constraint if exists api_key_user")
        run("drop index if exists api_key_user")
    }

    /** Each statement suits some databases only: the others refuse it, which is fine */
    private fun run(sql: String) {
        try {
            dataSource.connection.use { connection ->
                connection.createStatement().use { it.execute(sql) }
                if (!connection.autoCommit) connection.commit()
            }
        } catch (e: Exception) {
            logger.debug { "Skipped '$sql': ${e.message}" }
        }
    }
}
