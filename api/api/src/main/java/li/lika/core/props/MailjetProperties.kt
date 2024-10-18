package li.lika.core.props

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "mailjet")
class MailjetProperties {
    var mocked: Boolean = true
    var apiKey: String? = null
    var apiSecret: String? = null
    var senderEmail: String? = null
    var scoresEmail: String? = null
}
