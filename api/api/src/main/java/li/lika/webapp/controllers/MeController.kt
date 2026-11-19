package li.lika.webapp.controllers

import li.lika.common.models.*
import li.lika.common.security.LikaPrincipal
import li.lika.core.services.auth.ApiKeyService
import li.lika.core.services.auth.SessionService
import li.lika.core.services.auth.UserService
import li.lika.core.services.mcp.ClaudePlugin
import org.springframework.http.ContentDisposition
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.*

/**
 * The signed-in user. Not available to API keys.
 */
@RequestMapping("/api/me")
@RestController
class MeController(
    private val sessions: SessionService,
    private val users: UserService,
    private val keys: ApiKeyService,
    private val plugin: ClaudePlugin
) {

    @GetMapping
    fun me(@AuthenticationPrincipal p: LikaPrincipal): Me = sessions.me(p)

    @PatchMapping
    fun update(@AuthenticationPrincipal p: LikaPrincipal, @RequestBody req: ProfileUpdate): Me = sessions.updateProfile(p, req)

    /** Signs out the other sessions; the returned tokens replace the current ones */
    @PostMapping("/password")
    fun changePassword(@AuthenticationPrincipal p: LikaPrincipal, @RequestBody req: ChangePasswordRequest): AuthTokens = sessions.changePassword(p, req)

    /** Signs out everywhere, this session included */
    @PostMapping("/sessions/revoke")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun revokeSessions(@AuthenticationPrincipal p: LikaPrincipal) {
        users.revokeSessions(p, p.id)
    }

    /** The user API keys of the user, oldest first, each with when its application last used it */
    @GetMapping("/api-keys")
    fun apiKeys(@AuthenticationPrincipal p: LikaPrincipal): List<ApiKey> = keys.userKeys(p.id)

    /**
     * Creates a user API key for one application. It acts with the user's current rights,
     * on the MCP server only. The secret is only returned here.
     */
    @PostMapping("/api-keys")
    fun createApiKey(@AuthenticationPrincipal p: LikaPrincipal, @RequestBody req: UserKeyRequest): ApiKeySecret =
        keys.createUserKey(p as LikaPrincipal.UserPrincipal, req.name)

    /**
     * Claude plugin bringing the skills of the MCP server, for the rights of the user now, and the server as connector.
     * [mcpUrl], the URL the web interface shows, is used when LIKA_PUBLIC_API_URL is not set.
     */
    @GetMapping("/claude-plugin")
    fun claudePlugin(@AuthenticationPrincipal p: LikaPrincipal, @RequestParam(required = false) mcpUrl: String?): ResponseEntity<ByteArray> =
        ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(ClaudePlugin.FILE_NAME).build().toString())
            .contentType(MediaType.parseMediaType("application/zip"))
            .body(plugin.zip(p, mcpUrl))

    /** The application using it is disconnected immediately; the other keys keep working */
    @DeleteMapping("/api-keys/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun deleteApiKey(@AuthenticationPrincipal p: LikaPrincipal, @PathVariable id: String) {
        keys.deleteUserKey(p.id, id)
    }
}
