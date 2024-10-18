package li.lika.core.props

import li.lika.common.models.Domain
import org.springframework.boot.context.properties.ConfigurationProperties

data class DefaultLink(val slug: String, val targetUrl: String, val domainIndex: Int)

@ConfigurationProperties(prefix = "lika")
class LikaProperties {
    var domains: List<Domain>? = null
    var defaultLinks: List<DefaultLink>? = null
}
