package li.lika.core.services.auth

import li.lika.common.exceptions.*
import li.lika.common.models.*
import li.lika.common.persistence.UserTokenDao
import li.lika.common.security.LikaPrincipal
import li.lika.core.props.AuthProperties
import li.lika.core.props.McpProperties
import mu.KotlinLogging
import org.springframework.stereotype.Service
import java.security.MessageDigest
import java.time.Duration
import java.time.Instant
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Sessions, and the flows reached from a link: first setup, invitation, password reset.
 *
 * With the local provider, Lika signs access tokens and keeps refresh tokens. With an external provider, the
 * provider's tokens are handed to the web app as they are; Lika links them to its users and refuses them when
 * the user is disabled or was signed out (sessionsNotBefore).
 */
@Service
class SessionService(
    private val users: UserService,
    private val tokenDao: UserTokenDao,
    private val provider: IdentityProvider,
    private val mail: MailService,
    private val props: AuthProperties,
    private val mcp: McpProperties,
    private val keys: ApiKeyService
) {
    private val logger = KotlinLogging.logger {}
    private val failures = ConcurrentHashMap<String, Pair<Int, Instant>>()

    @Volatile
    private var setupToken: String? = null

    companion object {
        private const val MAX_FAILURES = 10
        private val FAILURE_WINDOW: Duration = Duration.ofMinutes(15)
    }

    private val local: LocalIdentityProvider?
        get() = provider as? LocalIdentityProvider

    fun config(): AuthConfig = AuthConfig(
        provider = provider.type,
        setupRequired = !users.hasActiveAdmin(),
        mailEnabled = mail.enabled,
        mcpUrl = mcp.url,
        passwordSignIn = provider.passwordSignIn,
        sso = provider.sso(),
        accountUrl = provider.accountUrl(),
        passwordReset = when {
            !provider.passwordSignIn -> PasswordReset.PROVIDER
            mail.enabled || provider.type == AuthProviderType.FIREBASE -> PasswordReset.EMAIL
            else -> PasswordReset.ADMIN
        }
    )

    // --- First setup

    /**
     * Prints the setup token while no admin exists, so that only someone reading the logs can create the first one.
     */
    fun announceSetup() {
        if (users.hasActiveAdmin()) {
            setupToken = null
            return
        }
        val token = props.setupToken?.takeIf { it.isNotBlank() } ?: Secrets.random(24)
        setupToken = token
        logger.warn(
            "\n=================================================================\n" +
            "  Lika has no admin yet. Create yours in the web app with\n" +
            "  the setup token: {}\n" +
            "=================================================================", token
        )
    }

    /**
     * @return the session of the new admin, or null when they must now sign in with the identity provider
     */
    fun setup(req: SetupRequest): AuthTokens? {
        if (users.hasActiveAdmin()) {
            throw SetupException.alreadyDone()
        }
        val expected = setupToken ?: throw SetupException.alreadyDone()
        if (!MessageDigest.isEqual(expected.toByteArray(), req.setupToken.trim().toByteArray())) {
            throw SetupException.invalidToken()
        }
        val email = UserService.normalizeEmail(req.email)
        val existing = users.findByEmail(email)
        val admin = (existing ?: User(UUID.randomUUID().toString(), email, "", UserStatus.ACTIVE)).copy(
            name = UserService.normalizeName(req.name, email),
            status = UserStatus.ACTIVE,
            admin = true,
            domains = emptyMap(),
            version = (existing?.version ?: 0) + 1
        )
        val tokens = activate(admin, req.password.takeIf { it.isNotEmpty() })
        setupToken = null
        logger.info("First admin {} created", email)
        return tokens
    }

    // --- Sign-in

    fun login(req: LoginRequest): AuthTokens {
        val email = req.email.trim().lowercase()
        assertNotThrottled(email)
        if (local == null) {
            if (!provider.passwordSignIn) throw UnsupportedByProviderException("Sign in with ${provider.sso()?.label ?: "the identity provider"}")
            val session = try {
                provider.login(email, req.password)
            } catch (e: InvalidCredentialsException) {
                recordFailure(email)
                throw e
            }
            failures.remove(email)
            return open(session)
        }
        val user = users.findByEmail(email)
        if (user == null || user.status == UserStatus.INVITED || !users.passwordMatches(user, req.password)) {
            recordFailure(email)
            throw InvalidCredentialsException("Wrong email or password")
        }
        failures.remove(email)
        if (user.status == UserStatus.DISABLED) {
            throw UserDisabledException()
        }
        return openLocal(user)
    }

    /** Single sign-on: exchanges the authorization code the web app received */
    fun exchangeCode(req: SsoTokenRequest): AuthTokens {
        require(req.code.isNotBlank() && req.codeVerifier.isNotBlank() && req.redirectUri.isNotBlank()) { "code, codeVerifier and redirectUri are required" }
        return open(provider.exchangeCode(req.code, req.codeVerifier, req.redirectUri))
    }

    fun refresh(req: RefreshRequest): AuthTokens {
        if (local == null) {
            return open(provider.refresh(req.refreshToken), signIn = false)
        }
        val token = tokenDao.findByHash(UserTokenType.REFRESH, Secrets.sha256(req.refreshToken))
            ?: throw InvalidCredentialsException("Session expired")
        tokenDao.deleteOne(token.id)
        if (token.isExpired()) {
            throw InvalidCredentialsException("Session expired")
        }
        val user = users.findCached(token.userId)?.takeIf { it.status == UserStatus.ACTIVE }
            ?: throw InvalidCredentialsException("Session expired")
        return issueLocal(user)
    }

    fun logout(req: RefreshRequest) {
        if (local == null) {
            provider.logout(req.refreshToken)
            return
        }
        tokenDao.findByHash(UserTokenType.REFRESH, Secrets.sha256(req.refreshToken))?.let { tokenDao.deleteOne(it.id) }
    }

    /**
     * Links the provider session to its Lika user, and checks that this user may still use it.
     * When the token does not carry the current rights yet (new account, failed write), they are written
     * and the session is renewed once to carry them.
     */
    private fun open(session: ProviderSession, signIn: Boolean = true): AuthTokens {
        val user = users.resolve(session.verified)
        assertMayUse(user, session.verified)
        val current = if (session.verified.claims?.v != user.version) {
            provider.pushClaims(user)
            provider.refresh(session.refreshToken)
        } else {
            session
        }
        val saved = if (signIn) users.save(user.copy(lastLoginAt = Instant.now())) else user
        return AuthTokens(current.accessToken, current.expiresIn, current.refreshToken, saved)
    }

    /** Also checked on every request, by the credentials service */
    fun assertMayUse(user: User, verified: VerifiedToken) {
        if (user.status == UserStatus.DISABLED) throw UserDisabledException()
        if (user.status != UserStatus.ACTIVE) throw InvalidCredentialsException("This user cannot sign in")
        val notBefore = user.sessionsNotBefore ?: return
        val authTime = verified.authTime
        if (authTime != null && authTime.isBefore(notBefore.minusSeconds(1))) throw InvalidCredentialsException("Session expired")
        // Providers without auth_time (Keycloak): the sessions open at revocation time were recorded
        if (authTime == null && verified.sessionId != null && users.isRevokedSession(verified.sessionId!!)) throw InvalidCredentialsException("Session expired")
    }

    private fun openLocal(user: User): AuthTokens {
        return issueLocal(users.save(user.copy(lastLoginAt = Instant.now())))
    }

    private fun issueLocal(user: User): AuthTokens {
        val (refresh, _) = users.newToken(user, UserTokenType.REFRESH, props.refreshTokenTtl)
        return AuthTokens(local!!.issueAccessToken(user), props.accessTokenTtl.seconds, refresh, user)
    }

    private fun assertNotThrottled(email: String) {
        val (count, since) = failures[email] ?: return
        if (since.plus(FAILURE_WINDOW).isBefore(Instant.now())) {
            failures.remove(email)
        } else if (count >= MAX_FAILURES) {
            throw TooManyAttemptsException()
        }
    }

    private fun recordFailure(email: String) {
        failures.compute(email) { _, current ->
            val now = Instant.now()
            if (current == null || current.second.plus(FAILURE_WINDOW).isBefore(now)) 1 to now else (current.first + 1) to current.second
        }
    }

    // --- Invitations

    fun invitation(secret: String): InvitationInfo {
        val (_, user) = users.findToken(UserTokenType.INVITATION, secret)
        val accountExists = users.isExternal() && (user.externalId != null || provider.findAccount(user.email) != null)
        return InvitationInfo(user.email, user.name, accountExists)
    }

    /**
     * @return the session of the user, or null when they must now sign in with the identity provider
     */
    fun acceptInvitation(secret: String, req: AcceptInvitationRequest): AuthTokens? {
        val (token, user) = users.findToken(UserTokenType.INVITATION, secret)
        if (user.status != UserStatus.INVITED) {
            throw InvalidLinkTokenException()
        }
        val name = req.name?.trim()?.takeIf { it.isNotEmpty() } ?: user.name
        val tokens = activate(user.copy(name = name, status = UserStatus.ACTIVE), req.password.takeIf { it.isNotEmpty() })
        tokenDao.deleteOne(token.id)
        return tokens
    }

    /**
     * Saves an active user with their password, creating or linking their account at the provider.
     * An account the provider already has keeps its password: the user signs in with it.
     */
    private fun activate(user: User, password: String?): AuthTokens? {
        if (local != null) {
            val saved = users.save(user.copy(passwordHash = users.hashPassword(password ?: throw WeakPasswordException())))
            return openLocal(saved)
        }
        val existing = user.externalId ?: provider.findAccount(user.email)
        if (existing != null) {
            users.save(user.copy(externalId = existing))
            return null
        }
        users.checkPassword(password ?: throw WeakPasswordException())
        val externalId = provider.createAccount(user, password)
        users.save(user.copy(externalId = externalId))
        return if (provider.passwordSignIn) open(provider.login(user.email, password)) else null
    }

    // --- Passwords

    /** Answers the same whether the email is known or not */
    fun forgotPassword(req: ForgotPasswordRequest) {
        if (!provider.passwordSignIn) {
            throw UnsupportedByProviderException("Reset your password on the sign-in page of ${provider.sso()?.label ?: "the identity provider"}")
        }
        val user = users.findByEmail(req.email) ?: return
        if (user.status != UserStatus.ACTIVE) return
        when {
            mail.enabled -> users.sendPasswordReset(user)
            provider.sendPasswordResetEmail(user.email) -> logger.info("The identity provider emailed a password reset link to {}", user.email)
            else -> logger.warn("Password reset asked for {}, but no email can be sent: an admin must send a reset link", user.email)
        }
    }

    fun resetPassword(req: ResetPasswordRequest) {
        val (token, user) = users.findToken(UserTokenType.PASSWORD_RESET, req.token)
        if (user.status != UserStatus.ACTIVE) {
            throw InvalidLinkTokenException()
        }
        val updated = users.setPassword(user, req.password)
        tokenDao.deleteAllForUser(user.id, UserTokenType.PASSWORD_RESET)
        tokenDao.deleteAllForUser(user.id, UserTokenType.REFRESH)
        users.revokeProviderSessions(user)
        users.save(updated.copy(version = user.version + 1, sessionsNotBefore = Instant.now()))
        tokenDao.deleteOne(token.id)
    }

    // --- The signed-in user

    fun me(caller: LikaPrincipal): Me {
        val user = users.findById(caller.id)
        return Me(user, user.claims(), provider.type, keys.findUserKey(user.id))
    }

    fun updateProfile(caller: LikaPrincipal, req: ProfileUpdate): Me {
        val user = users.findById(caller.id)
        users.save(user.copy(name = UserService.normalizeName(req.name, user.email)))
        return me(caller)
    }

    /** Other sessions are signed out; the returned tokens keep this one going */
    fun changePassword(caller: LikaPrincipal, req: ChangePasswordRequest): AuthTokens {
        if (!provider.passwordSignIn) {
            throw UnsupportedByProviderException("Change your password in your ${provider.sso()?.label ?: "identity provider"} account")
        }
        val user = users.findById(caller.id)
        val currentMatches = if (local != null) users.passwordMatches(user, req.currentPassword)
                             else runCatching { provider.login(user.email, req.currentPassword) }.isSuccess
        if (!currentMatches) {
            throw WrongPasswordException()
        }
        val updated = users.setPassword(user, req.newPassword)
        tokenDao.deleteAllForUser(user.id, UserTokenType.REFRESH)
        val saved = users.save(updated.copy(version = user.version + 1, sessionsNotBefore = Instant.now()))
        return if (local != null) issueLocal(saved) else open(provider.login(user.email, req.newPassword), signIn = false)
    }
}
