package li.lika.webapp.controllers

import li.lika.common.models.*
import li.lika.core.services.auth.SessionService
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

/**
 * Public endpoints: signing in, and the flows reached from a link (first setup, invitation, password reset).
 */
@RequestMapping("/api/auth")
@RestController
class AuthController(private val sessions: SessionService) {

    @GetMapping("/config")
    fun config(): AuthConfig = sessions.config()

    /**
     * Creates the first admin, with the setup token printed in the API logs. Only while no admin exists.
     */
    /** 204 when the admin must now sign in with the identity provider */
    @PostMapping("/setup")
    fun setup(@RequestBody req: SetupRequest): ResponseEntity<AuthTokens> = orSignIn(sessions.setup(req))

    @PostMapping("/login")
    fun login(@RequestBody req: LoginRequest): AuthTokens = sessions.login(req)

    /** Renews the access token. The refresh token is replaced: use the new one next time. */
    /** Single sign-on: exchanges the authorization code the web app received from the identity provider */
    @PostMapping("/sso/token")
    fun ssoToken(@RequestBody req: SsoTokenRequest): AuthTokens = sessions.exchangeCode(req)

    @PostMapping("/refresh")
    fun refresh(@RequestBody req: RefreshRequest): AuthTokens = sessions.refresh(req)

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun logout(@RequestBody req: RefreshRequest) = sessions.logout(req)

    @GetMapping("/invitations/{token}")
    fun invitation(@PathVariable token: String): InvitationInfo = sessions.invitation(token)

    /** 204 when the user must now sign in with the identity provider */
    @PostMapping("/invitations/{token}/accept")
    fun accept(@PathVariable token: String, @RequestBody req: AcceptInvitationRequest): ResponseEntity<AuthTokens> = orSignIn(sessions.acceptInvitation(token, req))

    /** Emails a reset link if the user exists and emails are enabled. Always answers 204. */
    @PostMapping("/password/forgot")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun forgot(@RequestBody req: ForgotPasswordRequest) = sessions.forgotPassword(req)

    @PostMapping("/password/reset")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun reset(@RequestBody req: ResetPasswordRequest) = sessions.resetPassword(req)

    private fun orSignIn(tokens: AuthTokens?): ResponseEntity<AuthTokens> = tokens?.let { ResponseEntity.ok(it) } ?: ResponseEntity.noContent().build()
}
