package li.lika.security.props

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * Root account of Lika < 0.7.0. Deprecated: when set, its tokens become admin API keys at startup.
 */
@ConfigurationProperties(prefix = "security")
class SecurityProperties {
    var defaultGlobalAccessToken: String? = null
    var defaultGlobalPublicReadAccessToken: String? = null
    var defaultGlobalName: String? = null
}
