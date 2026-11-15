package li.lika.webapp

import li.lika.core.services.auth.AccessMigrationService
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.util.UUID

/**
 * As the images start: no profile, so none of the development defaults of application-dev.yml.
 */
@SpringBootTest
@AutoConfigureMockMvc
class ProductionDefaultsTest {

    @Autowired
    lateinit var mvc: MockMvc

    @Autowired
    lateinit var jdbc: JdbcTemplate

    @Autowired
    lateinit var migration: AccessMigrationService

    private fun status(token: String, path: String) =
        mvc.perform(get(path).header("Authorization", "Bearer $token").accept(MediaType.APPLICATION_JSON)).andReturn().response.status

    @Test
    fun `no public root key and no sample domain`() {
        assertEquals(401, status("lika-client-secret", "/api/admin/domains"))
        assertEquals(0, jdbc.queryForObject("select count(*) from domains", Int::class.java))
        mvc.perform(get("/api/auth/config")).andExpect(status().isOk)
    }

    @Test
    fun `accounts holding the public development tokens of Lika before 0_7_0 are not migrated`() {
        val id = UUID.randomUUID().toString()
        jdbc.update("insert into accounts (id, name, global, default_access_token, default_public_read_access_token) values (?, 'global', true, 'lika-client-secret', 'lika-client-read')", id)
        migration.migrate()
        assertEquals(401, status("lika-client-secret", "/api/admin/domains"))
        assertEquals(401, status("lika-client-read", "/api/domains"))
        assertEquals(0, jdbc.queryForObject("select count(*) from accounts", Int::class.java))
    }
}
