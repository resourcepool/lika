package li.lika.core.services.auth

import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import java.security.KeyPairGenerator
import java.util.Base64

class ServiceAccountKeyTest {

    private val mapper = ObjectMapper()

    /** A key file as Google downloads it */
    private val keyFile: String by lazy {
        val pair = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
        val pem = "-----BEGIN PRIVATE KEY-----\n" + Base64.getMimeEncoder(64, "\n".toByteArray()).encodeToString(pair.private.encoded) + "\n-----END PRIVATE KEY-----\n"
        mapper.writerWithDefaultPrettyPrinter().writeValueAsString(mapOf(
            "type" to "service_account", "project_id" to "lika-test", "private_key" to pem,
            "client_email" to "lika@lika-test.iam.gserviceaccount.com", "token_uri" to "https://oauth2.googleapis.com/token"
        ))
    }

    private fun assertParsed(value: String) {
        val key = ServiceAccountKey.parse(value, mapper)
        assertEquals("lika@lika-test.iam.gserviceaccount.com", key.email)
        assertEquals("https://oauth2.googleapis.com/token", key.tokenUri)
        assertEquals("RSA", key.key.algorithm)
    }

    @Test
    fun `reads the key file encoded in base64`() {
        assertParsed(Base64.getEncoder().encodeToString(keyFile.toByteArray()))
    }

    @Test
    fun `accepts base64 wrapped in lines, as base64 prints it by default, and the URL-safe alphabet`() {
        assertParsed(Base64.getMimeEncoder().encodeToString(keyFile.toByteArray()))
        assertParsed(Base64.getUrlEncoder().withoutPadding().encodeToString(keyFile.toByteArray()))
    }

    @Test
    fun `still reads the JSON itself, or the path to the file`(@TempDir dir: Path) {
        assertParsed(keyFile)
        val file = dir.resolve("key.json").also { Files.writeString(it, keyFile) }
        assertParsed(file.toString())
    }

    @Test
    fun `explains what is expected when the value is not a key`() {
        val notBase64 = assertThrows(IllegalStateException::class.java) { ServiceAccountKey.parse("not a key!", mapper) }
        assertTrue(notBase64.message!!.contains("base64 -w0 key.json"))
        val notJson = assertThrows(IllegalStateException::class.java) { ServiceAccountKey.parse(Base64.getEncoder().encodeToString("hello".toByteArray()), mapper) }
        assertTrue(notJson.message!!.contains("does not decode to JSON"))
        val notAKey = assertThrows(IllegalStateException::class.java) { ServiceAccountKey.parse(Base64.getEncoder().encodeToString("{\"a\":1}".toByteArray()), mapper) }
        assertTrue(notAKey.message!!.contains("client_email"))
    }
}
