package li.lika.core.services.auth

import li.lika.common.exceptions.ApiKeyNotFoundException
import li.lika.common.exceptions.ForbiddenException
import li.lika.common.models.*
import li.lika.common.persistence.ApiKeyDao
import li.lika.common.security.LikaPrincipal
import li.lika.core.services.AuthorizationService
import org.springframework.cache.CacheManager
import org.springframework.stereotype.Service
import java.time.Duration
import java.time.Instant
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * API keys of applications. Admins manage every key. Managers manage the keys whose domains they all manage.
 */
@Service
class ApiKeyService(
    private val keyDao: ApiKeyDao,
    private val authorization: AuthorizationService,
    private val cacheManager: CacheManager
) {
    companion object {
        const val KEYS_CACHE = "apikeys"
        private val TOUCH_EVERY: Duration = Duration.ofMinutes(10)
        const val MAX_USER_KEYS = 20
        const val MAX_NAME_LENGTH = 60
    }

    private val lastTouched = ConcurrentHashMap<String, Instant>()

    /** Cached by secret hash: used on every request made with a key */
    fun findBySecret(secret: String): ApiKey? {
        val hash = Secrets.sha256(secret)
        return cacheManager.getCache(KEYS_CACHE)?.get(hash) { keyDao.findBySecretHash(hash) } ?: keyDao.findBySecretHash(hash)
    }

    /** Records when the key was last used, at most every few minutes */
    fun touch(key: ApiKey) {
        val now = Instant.now()
        val last = lastTouched[key.id] ?: key.lastUsedAt
        if (last != null && last.plus(TOUCH_EVERY).isAfter(now)) return
        lastTouched[key.id] = now
        keyDao.findById(key.id)?.let { keyDao.save(it.copy(lastUsedAt = now)) }
    }

    fun list(caller: LikaPrincipal, domainId: String?): List<ApiKey> {
        val managed = authorization.managedDomainIds(caller)
        return keyDao.findAll()
            .filter { !it.userKey && mayManage(caller, it, managed) }
            .filter { domainId == null || it.admin || it.domains.containsKey(domainId) }
            .sortedBy { it.name.lowercase() }
    }

    fun get(caller: LikaPrincipal, id: String): ApiKey {
        val key = keyDao.findById(id)?.takeIf { !it.userKey } ?: throw ApiKeyNotFoundException()
        if (!mayManage(caller, key, authorization.managedDomainIds(caller))) throw ApiKeyNotFoundException()
        return key
    }

    fun create(caller: LikaPrincipal, req: ApiKeyRequest): ApiKeySecret {
        val name = req.name.trim()
        require(name.isNotEmpty()) { "Name the key after the application using it" }
        val domains = if (req.admin) emptyMap() else req.domains
        assertMayGrant(caller, req.admin, domains)
        val secret = Secrets.newApiKeySecret()
        val key = keyDao.save(
            ApiKey(
                id = UUID.randomUUID().toString(),
                name = name,
                admin = req.admin,
                domains = domains,
                secretHash = Secrets.sha256(secret),
                prefix = Secrets.displayPrefix(secret),
                createdBy = caller.id,
                expiresAt = req.expiresAt
            )
        )
        return ApiKeySecret(key, secret)
    }

    fun update(caller: LikaPrincipal, id: String, req: ApiKeyUpdate): ApiKey {
        val key = get(caller, id)
        val domains = if (key.admin) emptyMap() else req.domains ?: key.domains
        if (req.domains != null) assertMayGrant(caller, key.admin, domains)
        val name = req.name?.trim()?.takeIf { it.isNotEmpty() } ?: key.name
        val expiresAt = if (req.removeExpiry) null else req.expiresAt ?: key.expiresAt
        return save(key, key.copy(name = name, domains = domains, expiresAt = expiresAt))
    }

    /** The previous secret stops working immediately */
    fun regenerate(caller: LikaPrincipal, id: String): ApiKeySecret {
        val key = get(caller, id)
        val secret = Secrets.newApiKeySecret()
        return ApiKeySecret(save(key, key.copy(secretHash = Secrets.sha256(secret), prefix = Secrets.displayPrefix(secret))), secret)
    }

    fun delete(caller: LikaPrincipal, id: String) {
        val key = get(caller, id)
        keyDao.deleteOne(key.id)
        evict(key)
    }

    // --- User API keys

    fun userKeys(userId: String): List<ApiKey> = keyDao.findAllByUserId(userId).sortedBy { it.createdAt }

    /**
     * Creates a user API key of the caller for the application named [name]. Each application has its own,
     * to tell them apart and revoke one without disconnecting the others.
     */
    fun createUserKey(user: LikaPrincipal.UserPrincipal, name: String): ApiKeySecret {
        val application = name.trim()
        require(application.isNotEmpty()) { "Name the key after the application using it, e.g. Claude Desktop" }
        require(application.length <= MAX_NAME_LENGTH) { "At most $MAX_NAME_LENGTH characters for the application name" }
        val existing = keyDao.findAllByUserId(user.id)
        require(existing.none { it.name.equals(application, ignoreCase = true) }) { "You already have a key for $application: revoke it first, or choose another name" }
        require(existing.size < MAX_USER_KEYS) { "At most $MAX_USER_KEYS user API keys: revoke one you no longer use" }
        val secret = Secrets.newUserKeySecret()
        val key = keyDao.save(
            ApiKey(
                id = UUID.randomUUID().toString(),
                name = application,
                secretHash = Secrets.sha256(secret),
                prefix = Secrets.displayPrefix(secret),
                createdBy = user.id,
                userId = user.id
            )
        )
        return ApiKeySecret(key, secret)
    }

    fun deleteUserKey(userId: String, id: String) {
        val key = keyDao.findById(id)?.takeIf { it.userId == userId } ?: throw ApiKeyNotFoundException()
        keyDao.deleteOne(key.id)
        evict(key)
    }

    /** @return how many keys the user had */
    fun deleteUserKeys(userId: String): Int {
        val keys = keyDao.findAllByUserId(userId)
        keys.forEach {
            keyDao.deleteOne(it.id)
            evict(it)
        }
        return keys.size
    }

    /** Called when a domain is deleted. A key left without any domain is deleted too. */
    fun removeDomain(domainId: String) {
        keyDao.findAll().filter { !it.userKey && it.domains.containsKey(domainId) }.forEach {
            val domains = it.domains - domainId
            if (domains.isEmpty()) {
                keyDao.deleteOne(it.id)
                evict(it)
            } else {
                save(it, it.copy(domains = domains))
            }
        }
    }

    private fun mayManage(caller: LikaPrincipal, key: ApiKey, managed: Set<String>): Boolean {
        if (caller.admin) return true
        return !key.admin && key.domains.isNotEmpty() && key.domains.keys.all(managed::contains)
    }

    private fun assertMayGrant(caller: LikaPrincipal, admin: Boolean, domains: Map<String, DomainRole>) {
        require(domains.values.none { it == DomainRole.MANAGER }) { "API keys are viewers or editors: managing a domain is for users" }
        require(admin || domains.isNotEmpty()) { "Give the key a role on at least one domain" }
        authorization.assertDomainsExist(domains.keys)
        if (caller.admin) return
        if (admin) throw ForbiddenException()
        val managed = authorization.managedDomainIds(caller)
        if (domains.keys.any { it !in managed }) throw ForbiddenException()
    }

    fun save(previous: ApiKey?, key: ApiKey): ApiKey {
        val saved = keyDao.save(key)
        previous?.let { evict(it) }
        return saved
    }

    private fun evict(key: ApiKey) {
        cacheManager.getCache(KEYS_CACHE)?.evict(key.secretHash)
    }
}
