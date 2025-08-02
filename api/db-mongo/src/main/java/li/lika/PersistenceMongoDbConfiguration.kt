package li.lika

import com.mongodb.ConnectionString
import com.mongodb.MongoClientSettings
import com.mongodb.MongoCredential
import com.mongodb.ServerAddress
import com.mongodb.client.MongoClient
import com.mongodb.client.MongoClients
import li.lika.core.props.MongoProperties
import mu.KotlinLogging
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.data.mongodb.core.MongoTemplate


@Configuration
@EnableConfigurationProperties(MongoProperties::class)
class PersistenceMongoDbConfiguration {

    val logger = KotlinLogging.logger {}

    @Bean
    fun mongoClient(mongoProperties: MongoProperties): MongoClient {
        if (mongoProperties.connString != null) {
            val settings = MongoClientSettings.builder()
                    .applyConnectionString(ConnectionString(mongoProperties.connString!!))
                    .retryWrites(true)
                    .build()
            return MongoClients.create(settings)
        }
        val credentials = MongoCredential.createCredential(mongoProperties.username!!, mongoProperties.userDatabase!!, mongoProperties.password!!)
        val settings = MongoClientSettings.builder()
                .credential(credentials)
                .applyToClusterSettings { t -> t.hosts(mongoProperties.hosts.map { h -> ServerAddress(h.host!!, h.port!!) }) }
                .applyToSslSettings { t -> t.enabled(mongoProperties.tlsEnabled) }
                .build()
        return MongoClients.create(settings)
    }

    @Bean
    fun mongoTemplate(mongoClient: MongoClient, mongoProperties: MongoProperties): MongoTemplate {
        logger.info("Persistent Backend: MongoDB")
        logger.info(mongoProperties.hosts.toString())
        logger.info("Will connect to database ${mongoProperties.appDatabase}")
        return MongoTemplate(mongoClient, mongoProperties.appDatabase!!)
    }
}
