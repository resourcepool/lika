package li.lika.core.persistence.props

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * DB_POSTGRES_URL: jdbc:postgresql://host:5432/lika, or postgresql://user:password@host:5432/lika
 * (the connection URL Dokploy shows). DB_POSTGRES_USERNAME and DB_POSTGRES_PASSWORD override the URL's.
 */
@ConfigurationProperties(prefix = "db.postgres")
class PostgresProperties {
    var url: String? = null
    var username: String? = null
    var password: String? = null
    var maximumPoolSize: Int = 10
}
