package li.lika.core.persistence.props

import org.springframework.boot.context.properties.ConfigurationProperties
import java.nio.file.Path

@ConfigurationProperties(prefix = "db.sqlite")
class SQLiteProperties {
    var path: String = Path.of("./data/db.sqlite").normalize().toAbsolutePath().toString()
}
