package li.lika.webapp.controllers

import li.lika.common.models.*
import li.lika.common.security.LikaPrincipal
import li.lika.core.services.auth.UserService
import org.springframework.http.HttpStatus
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.*

/**
 * Admins manage every user. Managers see the users of the domains they manage, invite users to them
 * and set their roles there.
 */
@RequestMapping("/api/users")
@RestController
class UsersController(private val users: UserService) {

    @GetMapping
    fun list(@AuthenticationPrincipal p: LikaPrincipal, @RequestParam(required = false) status: String?, @RequestParam(required = false) domainId: String?): List<User> {
        val s = status?.let { v -> UserStatus.entries.firstOrNull { it.name.equals(v, true) } ?: throw IllegalArgumentException("Unknown status $v") }
        return users.list(p, s, domainId)
    }

    @GetMapping("/{id}")
    fun get(@AuthenticationPrincipal p: LikaPrincipal, @PathVariable id: String): User = users.get(p, id)

    /** Creates an invited user. The link is always returned; it is also emailed when emails are enabled. */
    @PostMapping("/invitations")
    fun invite(@AuthenticationPrincipal p: LikaPrincipal, @RequestBody req: InvitationRequest): UserLink = users.invite(p, req)

    /** Replaces the invitation link: the previous one stops working */
    @PostMapping("/{id}/invitation")
    fun resendInvitation(@AuthenticationPrincipal p: LikaPrincipal, @PathVariable id: String): UserLink = users.resendInvitation(p, id)

    /** Admin only. Name and email. */
    @PatchMapping("/{id}")
    fun update(@AuthenticationPrincipal p: LikaPrincipal, @PathVariable id: String, @RequestBody req: UserUpdate): User = users.update(p, id, req)

    /** Takes effect immediately: the user's current tokens are refused and renewed */
    @PutMapping("/{id}/access")
    fun updateAccess(@AuthenticationPrincipal p: LikaPrincipal, @PathVariable id: String, @RequestBody req: AccessUpdate): User = users.updateAccess(p, id, req)

    @PostMapping("/{id}/disable")
    fun disable(@AuthenticationPrincipal p: LikaPrincipal, @PathVariable id: String): User = users.disable(p, id)

    @PostMapping("/{id}/enable")
    fun enable(@AuthenticationPrincipal p: LikaPrincipal, @PathVariable id: String): User = users.enable(p, id)

    @PostMapping("/{id}/password-reset")
    fun passwordReset(@AuthenticationPrincipal p: LikaPrincipal, @PathVariable id: String): UserLink = users.passwordResetLink(p, id)

    @PostMapping("/{id}/sessions/revoke")
    fun revokeSessions(@AuthenticationPrincipal p: LikaPrincipal, @PathVariable id: String): User = users.revokeSessions(p, id)

    /** Admin only: revokes the user API key of the user */
    @DeleteMapping("/{id}/api-key")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun revokeApiKey(@AuthenticationPrincipal p: LikaPrincipal, @PathVariable id: String) = users.revokeUserKey(p, id)

    /** Admins delete anyone but the last admin. Managers withdraw pending invitations to their domains. */
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun delete(@AuthenticationPrincipal p: LikaPrincipal, @PathVariable id: String) = users.delete(p, id)
}
