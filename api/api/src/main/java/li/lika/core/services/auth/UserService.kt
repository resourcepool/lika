package li.lika.core.services.auth

import li.lika.common.exceptions.*
import li.lika.common.models.*
import li.lika.common.persistence.UserDao
import li.lika.common.persistence.UserTokenDao
import li.lika.common.security.LikaPrincipal
import li.lika.core.props.AuthProperties
import li.lika.core.services.AuthorizationService
import org.springframework.cache.CacheManager
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import mu.KotlinLogging
import java.time.Instant
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Lifecycle of the users: invitation, rights, profile, disabling and deletion.
 *
 * Admins manage everyone. Managers see the users having a role on the domains they manage,
 * invite users to those domains and set their roles there, but never touch admins.
 */
@Service
class UserService(
    private val userDao: UserDao,
    private val tokenDao: UserTokenDao,
    private val authorization: AuthorizationService,
    private val provider: IdentityProvider,
    private val mail: MailService,
    private val passwordEncoder: PasswordEncoder,
    private val props: AuthProperties,
    private val cacheManager: CacheManager,
    private val keys: ApiKeyService
) {
    private val logger = KotlinLogging.logger {}

    /** Provider id → Lika id, stable once linked */
    private val externalIds = ConcurrentHashMap<String, String>()

    private val external: Boolean
        get() = provider.type != AuthProviderType.LOCAL

    companion object {
        const val USERS_CACHE = "users"
        private val EMAIL = Regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")

        fun normalizeEmail(email: String): String {
            val e = email.trim().lowercase()
            require(EMAIL.matches(e)) { "Invalid email address" }
            return e
        }

        fun normalizeName(name: String, email: String): String = name.trim().ifEmpty { email.substringBefore("@") }
    }

    // --- Lookups

    /** Cached: used on every authenticated request */
    fun findCached(id: String): User? {
        return cacheManager.getCache(USERS_CACHE)?.get(id) { userDao.findById(id) } ?: userDao.findById(id)
    }

    fun findById(id: String): User = userDao.findById(id) ?: throw UserNotFoundException()

    fun findByEmail(email: String): User? = userDao.findByEmail(email.trim().lowercase())

    /**
     * The Lika user of a verified token. With an external provider, an unknown account is linked to the invited
     * user with the same verified email, which activates them.
     * @throws UserNotInvitedException when no Lika user matches
     */
    fun resolve(verified: VerifiedToken): User {
        if (!external) return findCached(verified.subject) ?: throw InvalidCredentialsException("Unknown user")
        externalIds[verified.subject]?.let { id -> findCached(id)?.takeIf { it.externalId == verified.subject }?.let { return it } }
        val user = userDao.findByExternalId(verified.subject) ?: link(verified)
        externalIds[verified.subject] = user.id
        return user
    }

    private fun link(verified: VerifiedToken): User {
        val email = verified.email?.takeIf { verified.emailVerified } ?: throw UserNotInvitedException()
        val user = userDao.findByEmail(email) ?: throw UserNotInvitedException()
        if (user.status == UserStatus.DISABLED) throw UserDisabledException()
        logger.info("Linking {} to their {} account", user.email, provider.type.name.lowercase())
        tokenDao.deleteAllForUser(user.id, UserTokenType.INVITATION)
        return save(user.copy(externalId = verified.subject, status = UserStatus.ACTIVE))
    }

    fun hasActiveAdmin(): Boolean = userDao.findAll().any { it.admin && it.status == UserStatus.ACTIVE }

    fun list(caller: LikaPrincipal, status: UserStatus?, domainId: String?): List<User> {
        val managed = authorization.managedDomainIds(caller)
        return userDao.findAll()
            .filter { caller.admin || it.domains.keys.any(managed::contains) }
            .filter { status == null || it.status == status }
            .filter { domainId == null || it.admin || it.domains.containsKey(domainId) }
            .sortedWith(compareBy({ it.status != UserStatus.ACTIVE }, { it.name.lowercase() }))
            .map { view(caller, it, managed) }
    }

    fun get(caller: LikaPrincipal, id: String): User {
        val managed = authorization.managedDomainIds(caller)
        val user = userDao.findById(id) ?: throw UserNotFoundException()
        if (!caller.admin && user.id != caller.id && user.domains.keys.none(managed::contains)) {
            throw UserNotFoundException()
        }
        return view(caller, user, managed)
    }

    /** Managers only see the roles on the domains they manage */
    private fun view(caller: LikaPrincipal, user: User, managed: Set<String>): User {
        if (caller.admin || user.id == caller.id) return user
        return user.copy(domains = user.domains.filterKeys(managed::contains))
    }

    // --- Invitations

    fun invite(caller: LikaPrincipal, req: InvitationRequest): UserLink {
        val email = normalizeEmail(req.email)
        assertMayGrant(caller, req.admin, req.domains.mapValues { it.value })
        if (!req.admin && req.domains.isEmpty()) {
            throw IllegalArgumentException("Give the user a role on at least one domain, or make them admin")
        }
        if (userDao.findByEmail(email) != null) {
            throw UserAlreadyExistsException()
        }
        val user = save(
            User(
                id = UUID.randomUUID().toString(),
                email = email,
                name = normalizeName(req.name, email),
                status = UserStatus.INVITED,
                admin = req.admin,
                domains = if (req.admin) emptyMap() else req.domains,
                invitedBy = caller.id
            )
        )
        return sendInvitation(user, caller.name)
    }

    fun resendInvitation(caller: LikaPrincipal, id: String): UserLink {
        val user = get(caller, id)
        if (user.status != UserStatus.INVITED) {
            throw IllegalArgumentException("This user already accepted the invitation")
        }
        if (!caller.admin && (user.admin || user.domains.isEmpty())) {
            throw ForbiddenException()
        }
        return sendInvitation(findById(id), caller.name)
    }

    private fun sendInvitation(user: User, inviterName: String?): UserLink {
        tokenDao.deleteAllForUser(user.id, UserTokenType.INVITATION)
        val (secret, token) = newToken(user, UserTokenType.INVITATION, props.invitationTtl)
        val link = "${webUrl()}/invite/$secret"
        val sent = mail.sendInvitation(user, link, inviterName, props.invitationTtl)
        return UserLink(user, link, sent, token.expiresAt)
    }

    // --- Rights

    fun updateAccess(caller: LikaPrincipal, id: String, req: AccessUpdate): User {
        val user = findById(id)
        if (!caller.admin && user.admin) {
            throw ForbiddenException()
        }
        val admin = if (caller.admin) req.admin ?: user.admin else user.admin
        assertMayGrant(caller, admin && !user.admin, req.domains)
        if (user.admin && !admin) {
            assertNotLastAdmin(user)
        }
        val domains = user.domains.toMutableMap()
        req.domains.forEach { (domainId, role) -> if (role == null) domains.remove(domainId) else domains[domainId] = role }
        val updated = save(user.copy(admin = admin, domains = if (admin) emptyMap() else domains, version = user.version + 1))
        return get(caller, updated.id)
    }

    /**
     * Admins grant anything. Managers grant roles, up to manager, on the domains they manage only.
     */
    private fun assertMayGrant(caller: LikaPrincipal, admin: Boolean, domains: Map<String, DomainRole?>) {
        authorization.assertDomainsExist(domains.keys)
        if (caller.admin) return
        if (admin) throw ForbiddenException()
        val managed = authorization.managedDomainIds(caller)
        if (domains.keys.any { it !in managed }) throw ForbiddenException()
    }

    /** Called when a domain is deleted */
    fun removeDomain(domainId: String) {
        userDao.findAll().filter { it.domains.containsKey(domainId) }.forEach {
            save(it.copy(domains = it.domains - domainId, version = it.version + 1))
        }
    }

    // --- Admin operations

    fun update(caller: LikaPrincipal, id: String, req: UserUpdate): User {
        authorization.assertAdmin(caller)
        val user = findById(id)
        val email = req.email?.let { normalizeEmail(it) } ?: user.email
        if (email != user.email && external && user.externalId != null) {
            throw UnsupportedByProviderException("The email of this user is managed by the identity provider")
        }
        if (email != user.email && userDao.findByEmail(email) != null) {
            throw UserAlreadyExistsException()
        }
        val name = req.name?.trim()?.takeIf { it.isNotEmpty() } ?: user.name
        return save(user.copy(email = email, name = name))
    }

    fun disable(caller: LikaPrincipal, id: String): User {
        authorization.assertAdmin(caller)
        val user = findById(id)
        if (user.status == UserStatus.DISABLED) return user
        assertNotLastAdmin(user)
        tokenDao.deleteAllForUser(user.id)
        revokeProviderSessions(user)
        return save(user.copy(status = UserStatus.DISABLED, version = user.version + 1, sessionsNotBefore = Instant.now()))
    }

    fun enable(caller: LikaPrincipal, id: String): User {
        authorization.assertAdmin(caller)
        val user = findById(id)
        if (user.status != UserStatus.DISABLED) return user
        val status = if (user.hasPassword || user.externalId != null || user.lastLoginAt != null) UserStatus.ACTIVE else UserStatus.INVITED
        return save(user.copy(status = status, version = user.version + 1))
    }

    fun delete(caller: LikaPrincipal, id: String) {
        val user = findById(id)
        // Managers may withdraw an invitation they could have sent
        val withdrawable = user.status == UserStatus.INVITED && !user.admin && user.domains.isNotEmpty() &&
                user.domains.keys.all(authorization.managedDomainIds(caller)::contains)
        if (!caller.admin && !withdrawable) {
            throw ForbiddenException()
        }
        assertNotLastAdmin(user)
        tokenDao.deleteAllForUser(user.id)
        keys.deleteUserKey(user.id)
        userDao.deleteOne(user.id)
        evict(user.id)
        user.externalId?.let { id ->
            externalIds.remove(id)
            // The account stays at the provider, which other applications may use: only its access to Lika goes
            runCatching { provider.clearClaims(id) }.onFailure { logger.warn("Could not remove the claims of {}: {}", user.email, it.message) }
        }
    }

    fun passwordResetLink(caller: LikaPrincipal, id: String): UserLink {
        authorization.assertAdmin(caller)
        val user = findById(id)
        if (user.status != UserStatus.ACTIVE) {
            throw IllegalArgumentException("Only active users can reset their password")
        }
        return sendPasswordReset(user)
    }

    fun sendPasswordReset(user: User): UserLink {
        tokenDao.deleteAllForUser(user.id, UserTokenType.PASSWORD_RESET)
        val (secret, token) = newToken(user, UserTokenType.PASSWORD_RESET, props.passwordResetTtl)
        val link = "${webUrl()}/reset-password/$secret"
        val sent = mail.sendPasswordReset(user, link, props.passwordResetTtl)
        return UserLink(user, link, sent, token.expiresAt)
    }

    /** Admin only: the user can create a new one */
    fun revokeUserKey(caller: LikaPrincipal, id: String) {
        authorization.assertAdmin(caller)
        findById(id)
        if (!keys.deleteUserKey(id)) throw ApiKeyNotFoundException()
    }

    /** Signs the user out everywhere: current tokens are refused, sessions cannot be renewed */
    fun revokeSessions(caller: LikaPrincipal, id: String): User {
        if (caller.id != id) authorization.assertAdmin(caller)
        val user = findById(id)
        tokenDao.deleteAllForUser(user.id, UserTokenType.REFRESH)
        revokeProviderSessions(user)
        return save(user.copy(version = user.version + 1, sessionsNotBefore = Instant.now()))
    }

    /**
     * Refuses the current sessions of the user at the provider, without ending them there:
     * other applications using the same provider are not affected.
     */
    fun revokeProviderSessions(user: User) {
        val externalId = user.externalId ?: return
        val expiresAt = Instant.now().plus(props.refreshTokenTtl)
        provider.sessionIds(externalId).forEach { sid ->
            tokenDao.save(UserToken(UUID.randomUUID().toString(), user.id, UserTokenType.REVOKED_SESSION, Secrets.sha256(sid), Instant.now(), expiresAt))
        }
    }

    fun isRevokedSession(sessionId: String): Boolean = tokenDao.findByHash(UserTokenType.REVOKED_SESSION, Secrets.sha256(sessionId)) != null

    private fun assertNotLastAdmin(user: User) {
        if (user.admin && user.status == UserStatus.ACTIVE &&
            userDao.findAll().none { it.id != user.id && it.admin && it.status == UserStatus.ACTIVE }) {
            throw LastAdminException()
        }
    }

    // --- Shared with the session service

    /**
     * Saves the user, and writes their rights to the identity provider when they changed.
     */
    fun save(user: User): User {
        val previous = userDao.findById(user.id)
        // The provider first: if it refuses the claims, nothing changes
        if (external && user.externalId != null &&
            (previous == null || previous.externalId != user.externalId || previous.claims() != user.claims())) {
            provider.pushClaims(user)
        }
        val saved = userDao.save(user)
        evict(saved.id)
        return saved
    }

    /** Local provider: stored hashed. External: set at the provider. */
    fun setPassword(user: User, password: String): User {
        if (!external) return user.copy(passwordHash = hashPassword(password))
        checkPassword(password)
        provider.setPassword(user.externalId ?: throw UnsupportedByProviderException("This user has no account at the identity provider yet"), password)
        return user
    }

    fun isExternal() = external

    fun checkPassword(password: String) {
        if (password.length < WeakPasswordException.MIN_LENGTH) throw WeakPasswordException()
    }

    private fun evict(id: String) {
        cacheManager.getCache(USERS_CACHE)?.evict(id)
    }

    fun hashPassword(password: String): String {
        checkPassword(password)
        return passwordEncoder.encode(password)
    }

    fun passwordMatches(user: User, password: String): Boolean =
        user.passwordHash != null && passwordEncoder.matches(password, user.passwordHash)

    /** @return the secret to hand out, and the stored token holding its hash */
    fun newToken(user: User, type: UserTokenType, ttl: java.time.Duration): Pair<String, UserToken> {
        val secret = Secrets.random()
        val now = Instant.now()
        val token = tokenDao.save(UserToken(UUID.randomUUID().toString(), user.id, type, Secrets.sha256(secret), now, now.plus(ttl)))
        return secret to token
    }

    /** Finds a valid token; consuming it is up to the caller */
    fun findToken(type: UserTokenType, secret: String): Pair<UserToken, User> {
        val token = tokenDao.findByHash(type, Secrets.sha256(secret)) ?: throw InvalidLinkTokenException()
        if (token.isExpired()) {
            tokenDao.deleteOne(token.id)
            throw InvalidLinkTokenException()
        }
        val user = userDao.findById(token.userId) ?: throw InvalidLinkTokenException()
        return token to user
    }

    private fun webUrl() = props.webUrl.trimEnd('/')

    @Scheduled(cron = "0 30 * * * *")
    fun purgeExpiredTokens() {
        tokenDao.deleteExpired(Instant.now())
    }
}
