package li.lika.core.persistence

import jakarta.annotation.PostConstruct
import li.lika.core.persistence.props.SQLiteProperties
import mu.KotlinLogging
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.env.Environment
import org.springframework.jdbc.datasource.DriverManagerDataSource
import java.nio.file.Files
import java.nio.file.Path
import javax.sql.DataSource


@Configuration
@EnableConfigurationProperties(SQLiteProperties::class)
class PersistenceSQLiteConfiguration {

    val logger = KotlinLogging.logger {}

    @PostConstruct
    fun init() {
        logger.info("Initialized SQLite Database")
    }
    @Bean
    fun dataSource(env: Environment, props: SQLiteProperties): DataSource {
        val dataSource = DriverManagerDataSource()
        val db = Path.of(props.path)
        if (Files.notExists(db)) {
            logger.info("Creating SQLite DB")
            if (Files.notExists(db.parent)) {
                logger.info("Creating SQLite Directories Path")
                Files.createDirectories(db.parent)
            }
            Files.createFile(db)
        }

        dataSource.setDriverClassName("org.sqlite.JDBC")
        dataSource.url = "jdbc:sqlite:${props.path}"
        dataSource.username = "sa"
        dataSource.password = "sa"
        return dataSource
    }

}
