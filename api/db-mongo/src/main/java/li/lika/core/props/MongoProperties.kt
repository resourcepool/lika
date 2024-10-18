package li.lika.core.props

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * @author Loïc Ortola on 02/08/2018
 */
@ConfigurationProperties(prefix = "db.mongodb")
class MongoProperties {

    data class Host(var host: String? = null, var port: Int? = null)

    var hosts: List<Host> = emptyList()
    var tlsEnabled: Boolean = false
    var connString: String? = null
    var username: String? = null
    var password: CharArray? = null
    var userDatabase: String? = null
    var appDatabase: String? = null
}
