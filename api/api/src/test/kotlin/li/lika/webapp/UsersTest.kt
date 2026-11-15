package li.lika.webapp

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*

class UsersTest : ApiTestSupport() {

    @Test
    fun `the first admin needs the setup token, once`() {
        val admin = adminSession()
        assertTrue(admin["user"]["admin"].asBoolean())
        assertFalse(anonymous(get("/api/auth/config"))!!["setupRequired"].asBoolean())
        val again = anonymous(post("/api/auth/setup").json(mapOf("setupToken" to SETUP_TOKEN, "email" to "other@lika.test", "password" to PASSWORD)), 409)!!
        assertEquals("setup-done", again["errorCode"].asText())
    }

    @Test
    fun `an invited user accepts once, then signs in with the claims given`() {
        val domain = createDomain()
        val invitation = invite(adminSession().access, mapOf(domain.id to "editor"))!!
        assertEquals("invited", invitation["user"]["status"].asText())
        assertFalse(invitation["emailSent"].asBoolean())
        assertTrue(invitation["link"].asText().startsWith("http://localhost:3000/invite/"))

        val info = anonymous(get("/api/auth/invitations/${invitation.linkToken}"))!!
        assertEquals(invitation["user"]["email"].asText(), info["email"].asText())
        anonymous(post("/api/auth/invitations/${invitation.linkToken}/accept").json(mapOf("password" to "short")), 400)
        val session = anonymous(post("/api/auth/invitations/${invitation.linkToken}/accept").json(mapOf("password" to PASSWORD)))!!
        anonymous(post("/api/auth/invitations/${invitation.linkToken}/accept").json(mapOf("password" to PASSWORD)), 400)

        val me = call(session.access, get("/api/me"))!!
        assertEquals("editor", me["claims"]["domains"][domain.id].asText())
        assertEquals("active", me["user"]["status"].asText())
        createLink(session.access, domain.id)
        assertEquals(session.userId, login(session.email).userId)
        login(session.email, "wrong password", 401)
    }

    @Test
    fun `invited users cannot sign in, and emails are unique`() {
        val invitation = invite(adminSession().access, admin = true)!!
        login(invitation["user"]["email"].asText(), PASSWORD, 401)
        call(adminSession().access, post("/api/users/invitations").json(mapOf("email" to invitation["user"]["email"].asText().uppercase(), "admin" to true)), 409)
    }

    @Test
    fun `changing the rights makes current tokens stale until refreshed`() {
        val d1 = createDomain()
        val d2 = createDomain()
        val user = createUser(mapOf(d1.id to "viewer"))
        createLink(user.access, d1.id, expectedStatus = 403)

        call(adminSession().access, put("/api/users/${user.userId}/access").json(mapOf("domains" to mapOf(d1.id to "editor", d2.id to "viewer"))))
        val stale = call(user.access, get("/api/domains"), 401)!!
        assertEquals("token-stale", stale["errorCode"].asText())

        val renewed = anonymous(post("/api/auth/refresh").json(mapOf("refreshToken" to user.refresh)))!!
        createLink(renewed.access, d1.id)
        assertEquals(2, call(renewed.access, get("/api/domains"))!!.size())
        // Refresh tokens are single-use
        anonymous(post("/api/auth/refresh").json(mapOf("refreshToken" to user.refresh)), 401)

        call(adminSession().access, put("/api/users/${user.userId}/access").json(mapOf("domains" to mapOf(d1.id to null))))
        val again = anonymous(post("/api/auth/refresh").json(mapOf("refreshToken" to renewed.refresh)))!!
        assertEquals(listOf(d2.id), call(again.access, get("/api/domains"))!!.map { it.id })
    }

    @Test
    fun `managers invite to their domains and only see their part`() {
        val managed = createDomain()
        val other = createDomain()
        val manager = createUser(mapOf(managed.id to "manager"))
        invite(manager.access, mapOf(other.id to "viewer"), expectedStatus = 403)
        invite(manager.access, admin = true, expectedStatus = 403)
        val invited = invite(manager.access, mapOf(managed.id to "editor"))!!
        assertEquals(manager.userId, invited["user"]["invitedBy"].asText())

        val outsider = createUser(mapOf(other.id to "editor"))
        val both = createUser(mapOf(managed.id to "viewer", other.id to "editor"))
        val visible = call(manager.access, get("/api/users"))!!
        val ids = visible.map { it.id }
        assertTrue(invited["user"].id in ids)
        assertFalse(outsider.userId in ids)
        assertEquals(setOf(managed.id), visible.first { it.id == both.userId }["domains"].fieldNames().asSequence().toSet())
        call(manager.access, get("/api/users/${outsider.userId}"), 404)

        call(manager.access, put("/api/users/${both.userId}/access").json(mapOf("domains" to mapOf(managed.id to "manager"))))
        call(manager.access, put("/api/users/${both.userId}/access").json(mapOf("domains" to mapOf(other.id to null))), 403)
        call(manager.access, put("/api/users/${both.userId}/access").json(mapOf("admin" to true)))
        assertFalse(call(adminSession().access, get("/api/users/${both.userId}"))!!["admin"].asBoolean())

        call(manager.access, post("/api/users/${both.userId}/disable"), 403)
        call(manager.access, delete("/api/users/${both.userId}"), 403)
        call(manager.access, delete("/api/users/${invited["user"].id}"), 204)
    }

    @Test
    fun `managers cannot touch admins`() {
        val managed = createDomain()
        val manager = createUser(mapOf(managed.id to "manager"))
        val admin = createUser(admin = true)
        call(manager.access, put("/api/users/${admin.userId}/access").json(mapOf("domains" to mapOf(managed.id to "viewer"))), 403)
    }

    @Test
    fun `a disabled user is signed out everywhere until enabled`() {
        val domain = createDomain()
        val user = createUser(mapOf(domain.id to "editor"))
        val admin = adminSession().access
        assertEquals("disabled", call(admin, post("/api/users/${user.userId}/disable"))!!["status"].asText())
        call(user.access, get("/api/domains"), 401)
        anonymous(post("/api/auth/refresh").json(mapOf("refreshToken" to user.refresh)), 401)
        assertEquals("user-disabled", login(user.email, PASSWORD, 403)["errorCode"].asText())

        assertEquals("active", call(admin, post("/api/users/${user.userId}/enable"))!!["status"].asText())
        createLink(login(user.email).access, domain.id)
    }

    @Test
    fun `Lika always keeps an active admin`() {
        val admin = adminSession()
        call(admin.access, get("/api/users"))!!
            .filter { it["admin"].asBoolean() && it.id != admin.userId && it["status"].asText() == "active" }
            .forEach { call(admin.access, delete("/api/users/${it.id}"), 204) }
        assertEquals("last-admin", call(admin.access, post("/api/users/${admin.userId}/disable"), 409)!!["errorCode"].asText())
        call(admin.access, put("/api/users/${admin.userId}/access").json(mapOf("admin" to false)), 409)
        call(admin.access, delete("/api/users/${admin.userId}"), 409)
    }

    @Test
    fun `a reset link sets a new password and signs out every session`() {
        val user = createUser(mapOf(createDomain().id to "viewer"))
        val reset = call(adminSession().access, post("/api/users/${user.userId}/password-reset"))!!
        assertTrue(reset["link"].asText().contains("/reset-password/"))
        val newPassword = "a brand new password"
        anonymous(post("/api/auth/password/reset").json(mapOf("token" to reset.linkToken, "password" to newPassword)), 204)
        anonymous(post("/api/auth/password/reset").json(mapOf("token" to reset.linkToken, "password" to newPassword)), 400)
        call(user.access, get("/api/me"), 401)
        anonymous(post("/api/auth/refresh").json(mapOf("refreshToken" to user.refresh)), 401)
        login(user.email, PASSWORD, 401)
        login(user.email, newPassword)
        // Unknown emails get the same answer
        anonymous(post("/api/auth/password/forgot").json(mapOf("email" to "nobody@lika.test")), 204)
    }

    @Test
    fun `users change their own profile and password`() {
        val user = createUser(mapOf(createDomain().id to "viewer"))
        val other = login(user.email)
        assertEquals("New Name", call(user.access, patch("/api/me").json(mapOf("name" to "New Name")))!!["user"]["name"].asText())
        call(user.access, post("/api/me/password").json(mapOf("currentPassword" to "wrong", "newPassword" to "another long password")), 400)
        val renewed = call(user.access, post("/api/me/password").json(mapOf("currentPassword" to PASSWORD, "newPassword" to "another long password")))!!
        call(renewed.access, get("/api/me"))
        anonymous(post("/api/auth/refresh").json(mapOf("refreshToken" to other.refresh)), 401)

        call(renewed.access, post("/api/me/sessions/revoke"), 204)
        call(renewed.access, get("/api/me"), 401)
    }

    @Test
    fun `repeated failures lock the sign-in for a while`() {
        val user = createUser(mapOf(createDomain().id to "viewer"))
        repeat(10) { login(user.email, "wrong password", 401) }
        assertEquals("too-many-attempts", login(user.email, PASSWORD, 429)["errorCode"].asText())
    }

    @Test
    fun `deleting a domain removes the roles on it`() {
        val domain = createDomain()
        val kept = createDomain()
        val user = createUser(mapOf(domain.id to "editor", kept.id to "viewer"))
        val key = createKey(mapOf(domain.id to "editor"))
        call(ADMIN_TOKEN, delete("/api/admin/domains/${domain.id}"))
        val after = call(adminSession().access, get("/api/users/${user.userId}"))!!
        assertEquals(listOf(kept.id), after["domains"].fieldNames().asSequence().toList())
        call(key.secret, get("/api/domains"), 401)
    }
}
