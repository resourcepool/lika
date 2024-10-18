package li.lika.core.props

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "server")
class ServerProperties {
    var port: Int? = null
}
