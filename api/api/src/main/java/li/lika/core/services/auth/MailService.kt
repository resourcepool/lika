package li.lika.core.services.auth

import li.lika.common.models.User
import li.lika.core.props.MailjetProperties
import mu.KotlinLogging
import org.springframework.http.MediaType
import org.springframework.stereotype.Service
import org.springframework.web.client.RestClient
import org.springframework.web.util.HtmlUtils
import java.time.Duration

/**
 * Sends the emails of the user lifecycle through Mailjet, when it is configured.
 * Without it, the links are only returned to the admin, who passes them on.
 */
@Service
class MailService(private val props: MailjetProperties) {
    private val logger = KotlinLogging.logger {}
    private val client: RestClient by lazy { RestClient.builder().baseUrl(props.apiUrl).build() }

    val enabled: Boolean
        get() = props.enabled

    /** @return whether the email was sent */
    fun sendInvitation(user: User, link: String, invitedBy: String?, ttl: Duration): Boolean {
        val who = invitedBy?.let { "${esc(it)} invited you" } ?: "You are invited"
        return send(
            user, "You are invited to Lika",
            """
            <p>Hi ${esc(user.name)},</p>
            <p>$who to manage short links on Lika.</p>
            <p>${button(link, "Accept the invitation")}</p>
            <p>This link works once and expires in ${days(ttl)}.</p>
            """,
            "$who to manage short links on Lika.\n\nAccept the invitation: $link\n\nThis link works once and expires in ${days(ttl)}."
        )
    }

    fun sendPasswordReset(user: User, link: String, ttl: Duration): Boolean {
        return send(
            user, "Reset your Lika password",
            """
            <p>Hi ${esc(user.name)},</p>
            <p>Someone asked to reset the password of your Lika account. If it was not you, ignore this email.</p>
            <p>${button(link, "Choose a new password")}</p>
            <p>This link works once and expires in ${hours(ttl)}.</p>
            """,
            "Someone asked to reset the password of your Lika account. If it was not you, ignore this email.\n\nChoose a new password: $link\n\nThis link works once and expires in ${hours(ttl)}."
        )
    }

    private fun send(user: User, subject: String, html: String, text: String): Boolean {
        if (!enabled) {
            return false
        }
        val body = mapOf(
            "Messages" to listOf(
                mapOf(
                    "From" to mapOf("Email" to props.senderEmail, "Name" to props.senderName),
                    "To" to listOf(mapOf("Email" to user.email, "Name" to user.name)),
                    "Subject" to subject,
                    "TextPart" to text,
                    "HTMLPart" to layout(html)
                )
            )
        )
        return try {
            client.post()
                .headers { it.setBasicAuth(props.apiKey!!, props.apiSecret!!) }
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .toBodilessEntity()
            logger.info("Sent \"{}\" to {}", subject, user.email)
            true
        } catch (e: Exception) {
            logger.error("Could not send \"{}\" to {}: {}", subject, user.email, e.message)
            false
        }
    }

    private fun esc(s: String) = HtmlUtils.htmlEscape(s)

    private fun days(d: Duration) = if (d.toDays() >= 1) "${d.toDays()} day${if (d.toDays() > 1) "s" else ""}" else hours(d)

    private fun hours(d: Duration) = "${d.toHours()} hour${if (d.toHours() > 1) "s" else ""}"

    private fun button(link: String, label: String) =
        """<a href="${esc(link)}" style="display:inline-block;padding:10px 18px;border-radius:8px;background:#a3e635;color:#0a0a0a;font-weight:600;text-decoration:none">${esc(label)}</a>"""

    private fun layout(content: String) = """
        <div style="font-family:-apple-system,Segoe UI,Helvetica,Arial,sans-serif;font-size:15px;line-height:1.5;color:#18181b;max-width:520px;margin:0 auto;padding:24px">
          <p style="font-weight:700;font-size:18px;margin:0 0 16px">lika</p>
          ${content.trimIndent()}
        </div>
    """.trimIndent()
}
