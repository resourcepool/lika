package li.lika.core.props

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "cors")
class CorsProperties {
    var enabled: Boolean? = false
    var allowedOrigins: List<String>? = null
}
