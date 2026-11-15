package li.lika.core.services.auth

import com.fasterxml.jackson.databind.ObjectMapper
import java.nio.file.Files
import java.nio.file.Path
import java.security.KeyFactory
import java.security.interfaces.RSAPrivateKey
import java.security.spec.PKCS8EncodedKeySpec
import java.util.Base64

/**
 * Google service account key, as downloaded from the Google Cloud or Firebase console.
 */
data class ServiceAccountKey(val email: String, val key: RSAPrivateKey, val tokenUri: String) {

    companion object {
        private const val FORMATS = "the base64 of the service account key file (`base64 -w0 key.json`, or `base64 -i key.json` on macOS)"

        /**
         * Reads LIKA_FIREBASE_SERVICE_ACCOUNT: the base64 of the key file. Its JSON content, or the path to the file,
         * are also accepted.
         */
        fun parse(value: String, mapper: ObjectMapper): ServiceAccountKey {
            val trimmed = value.trim()
            val json = when {
                trimmed.startsWith("{") -> trimmed
                looksLikePath(trimmed) && Files.isRegularFile(Path.of(trimmed)) -> Files.readString(Path.of(trimmed))
                else -> decodeBase64(trimmed)
            }
            val node = runCatching { mapper.readTree(json) }.getOrNull()
                ?: throw IllegalStateException("LIKA_FIREBASE_SERVICE_ACCOUNT is not a valid key: set it to $FORMATS")
            val email = node.path("client_email").asText()
            val pem = node.path("private_key").asText()
            check(email.isNotBlank() && pem.isNotBlank()) { "LIKA_FIREBASE_SERVICE_ACCOUNT has no client_email or private_key: is it a service account key?" }
            val der = Base64.getDecoder().decode(pem.replace(Regex("-----[A-Z ]+-----|\\s"), ""))
            val key = KeyFactory.getInstance("RSA").generatePrivate(PKCS8EncodedKeySpec(der)) as RSAPrivateKey
            return ServiceAccountKey(email, key, node.path("token_uri").asText("https://oauth2.googleapis.com/token"))
        }

        private fun looksLikePath(value: String) = value.contains('/') && !value.contains(Regex("\\s")) && value.endsWith(".json")

        /** Standard or URL-safe alphabet, with or without padding and line breaks */
        private fun decodeBase64(value: String): String {
            val compact = value.replace(Regex("\\s"), "")
            val bytes = runCatching { Base64.getDecoder().decode(compact) }
                .recoverCatching { Base64.getUrlDecoder().decode(compact) }
                .getOrElse { throw IllegalStateException("LIKA_FIREBASE_SERVICE_ACCOUNT is neither JSON nor base64: set it to $FORMATS") }
            return String(bytes, Charsets.UTF_8).trim().also {
                check(it.startsWith("{")) { "LIKA_FIREBASE_SERVICE_ACCOUNT does not decode to JSON: set it to $FORMATS" }
            }
        }
    }
}
