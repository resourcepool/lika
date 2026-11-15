package li.lika.core.props

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * Emails are sent through Mailjet when the key, the secret and the sender are set.
 */
@ConfigurationProperties(prefix = "mailjet")
class MailjetProperties {
    /** MAILJET_API_KEY */
    var apiKey: String? = null
    /** MAILJET_API_SECRET */
    var apiSecret: String? = null
    /** MAIL_FROM */
    var senderEmail: String? = null
    /** MAIL_FROM_NAME */
    var senderName: String = "Lika"
    var apiUrl: String = "https://api.mailjet.com/v3.1/send"

    val enabled: Boolean
        get() = !apiKey.isNullOrBlank() && !apiSecret.isNullOrBlank() && !senderEmail.isNullOrBlank()
}
