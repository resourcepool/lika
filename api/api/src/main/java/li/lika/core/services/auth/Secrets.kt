package li.lika.core.services.auth

import java.security.MessageDigest
import java.security.SecureRandom

object Secrets {
    private val random = SecureRandom()
    private const val ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789"
    const val API_KEY_PREFIX = "lika_"

    fun random(length: Int = 43): String = buildString(length) {
        repeat(length) { append(ALPHABET[random.nextInt(ALPHABET.length)]) }
    }

    const val USER_KEY_PREFIX = "lika_u_"

    fun newApiKeySecret(): String = API_KEY_PREFIX + random(40)

    fun newUserKeySecret(): String = USER_KEY_PREFIX + random(40)

    /** High-entropy secrets need no slow hash: SHA-256 keeps every request cheap */
    fun sha256(value: String): String =
        MessageDigest.getInstance("SHA-256").digest(value.toByteArray()).joinToString("") { "%02x".format(it) }

    fun displayPrefix(secret: String): String = secret.take(
        when {
            secret.startsWith(USER_KEY_PREFIX) -> USER_KEY_PREFIX.length + 4
            secret.startsWith(API_KEY_PREFIX) -> API_KEY_PREFIX.length + 4
            else -> 4
        }
    )
}
