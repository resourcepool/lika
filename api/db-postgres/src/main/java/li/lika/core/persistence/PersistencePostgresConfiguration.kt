package li.lika.core.persistence

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import li.lika.core.persistence.props.PostgresProperties
import mu.KotlinLogging
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.net.URI
import java.net.URLDecoder
import javax.sql.DataSource

@Configuration
@EnableConfigurationProperties(PostgresProperties::class)
class PersistencePostgresConfiguration {

    private val logger = KotlinLogging.logger {}

    @Bean
    fun dataSource(props: PostgresProperties): DataSource {
        val connection = PostgresConnection.parse(
            props.url?.takeIf { it.isNotBlank() } ?: throw IllegalStateException("Set DB_POSTGRES_URL, e.g. postgresql://lika:password@postgres:5432/lika")
        )
        val config = HikariConfig().apply {
            jdbcUrl = connection.jdbcUrl
            username = props.username?.takeIf { it.isNotBlank() } ?: connection.username
            password = props.password?.takeIf { it.isNotBlank() } ?: connection.password
            maximumPoolSize = props.maximumPoolSize
            poolName = "lika"
        }
        logger.info("Persistent Backend: PostgreSQL at {}", connection.jdbcUrl)
        return HikariDataSource(config)
    }
}

data class PostgresConnection(val jdbcUrl: String, val username: String?, val password: String?) {
    companion object {
        /** Accepts JDBC URLs, and postgres:// or postgresql:// URIs carrying the credentials */
        fun parse(url: String): PostgresConnection {
            if (url.startsWith("jdbc:")) return PostgresConnection(url, null, null)
            val uri = URI(url)
            require(uri.scheme == "postgres" || uri.scheme == "postgresql") { "DB_POSTGRES_URL must start with jdbc:postgresql://, postgresql:// or postgres://" }
            val (user, password) = (uri.rawUserInfo ?: "").split(":", limit = 2).map { URLDecoder.decode(it, Charsets.UTF_8) }.let {
                it.getOrNull(0)?.takeIf { u -> u.isNotEmpty() } to it.getOrNull(1)
            }
            val port = if (uri.port == -1) 5432 else uri.port
            val query = uri.rawQuery?.let { "?$it" } ?: ""
            return PostgresConnection("jdbc:postgresql://${uri.host}:$port${uri.rawPath}$query", user, password)
        }
    }
}
