package li.lika.webapp.controllers

import li.lika.common.models.*
import li.lika.common.security.LikaPrincipal
import li.lika.core.services.auth.ApiKeyService
import li.lika.core.services.auth.SessionService
import li.lika.core.services.auth.UserService
import org.springframework.http.HttpStatus
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.*

/**
 * The signed-in user. Not available to API keys.
 */
@RequestMapping("/api/me")
@RestController
class MeController(private val sessions: SessionService, private val users: UserService, private val keys: ApiKeyService) {

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

    /**
     * Creates the user API key, replacing the previous one. It acts with the user's current rights,
     * on the MCP server only. The secret is only returned here.
     */
    @PostMapping("/api-key")
    fun createApiKey(@AuthenticationPrincipal p: LikaPrincipal): ApiKeySecret = keys.createUserKey(p as LikaPrincipal.UserPrincipal)

    @DeleteMapping("/api-key")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun deleteApiKey(@AuthenticationPrincipal p: LikaPrincipal) {
        keys.deleteUserKey(p.id)
    }
}
