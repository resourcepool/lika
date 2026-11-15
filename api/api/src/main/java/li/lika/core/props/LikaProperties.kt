package li.lika.core.props

import li.lika.common.models.Domain
import org.springframework.boot.context.properties.ConfigurationProperties

data class DefaultLink(val slug: String, val targetUrl: String, val type: String?, val password: String?, val domainIndex: Int)

@ConfigurationProperties(prefix = "lika")
class LikaProperties {
    var domains: List<Domain>? = null
    var defaultLinks: List<DefaultLink>? = null
    /** Header carrying the original host of requests forwarded by a CDN, e.g. X-Lika-Host (LIKA_HOST_HEADER) */
    var hostHeader: String? = null
}
