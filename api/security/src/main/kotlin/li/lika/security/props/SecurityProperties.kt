package li.lika.security.props

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "security")
class SecurityProperties {
    var defaultGlobalAccessToken: String? = null
    var defaultGlobalPublicReadAccessToken: String? = null
    var defaultGlobalName: String? = null
}
