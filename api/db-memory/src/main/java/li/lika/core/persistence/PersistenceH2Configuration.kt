package li.lika.core.persistence

import jakarta.annotation.PostConstruct
import li.lika.core.persistence.props.H2Properties
import mu.KotlinLogging
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Configuration

@Configuration
@EnableConfigurationProperties(H2Properties::class)
class PersistenceH2Configuration {

    val logger = KotlinLogging.logger {}

    @PostConstruct
    fun init() {
        logger.info("Initialized H2 in-memory Database")
    }


}
