package li.lika.core.persistence.models

import jakarta.persistence.*
import li.lika.common.models.*
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset

internal fun Instant.toUtc(): LocalDateTime = LocalDateTime.ofInstant(this, ZoneOffset.UTC)
internal fun LocalDateTime.toInstantUtc(): Instant = toInstant(ZoneOffset.UTC)

@Entity
@Table(
    name = "users",
    indexes = [
        Index(name = "user_email", columnList = "email", unique = true),
        Index(name = "user_external_id", columnList = "externalId", unique = false)
    ]
)
class UserDb() {
    @Id
    var id: String = ""
    var email: String = ""
    var name: String = ""
    var status: String = UserStatus.INVITED.name
    var admin: Boolean = false

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "user_domain_roles", joinColumns = [JoinColumn(name = "user_id")])
    @MapKeyColumn(name = "domain_id")
    @Column(name = "role")
    var domains: MutableMap<String, String> = mutableMapOf()

    var version: Long = 1
    var passwordHash: String? = null
    var externalId: String? = null
    var invitedBy: String? = null
    var createdAt: LocalDateTime = LocalDateTime.now(ZoneOffset.UTC)
    var lastLoginAt: LocalDateTime? = null
    var sessionsNotBefore: LocalDateTime? = null

    fun toDomain(): User = User(
        id = id,
        email = email,
        name = name,
        status = UserStatus.valueOf(status),
        admin = admin,
        domains = domains.mapValues { DomainRole.parse(it.value) },
        version = version,
        passwordHash = passwordHash,
        externalId = externalId,
        invitedBy = invitedBy,
        createdAt = createdAt.toInstantUtc(),
        lastLoginAt = lastLoginAt?.toInstantUtc(),
        sessionsNotBefore = sessionsNotBefore?.toInstantUtc()
    )
}

fun User.toDb(): UserDb = UserDb().also {
    it.id = id
    it.email = email
    it.name = name
    it.status = status.name
    it.admin = admin
    it.domains = domains.mapValues { d -> d.value.value }.toMutableMap()
    it.version = version
    it.passwordHash = passwordHash
    it.externalId = externalId
    it.invitedBy = invitedBy
    it.createdAt = createdAt.toUtc()
    it.lastLoginAt = lastLoginAt?.toUtc()
    it.sessionsNotBefore = sessionsNotBefore?.toUtc()
}

@Entity
@Table(
    name = "user_tokens",
    indexes = [
        Index(name = "user_token_hash", columnList = "tokenHash", unique = true),
        Index(name = "user_token_user", columnList = "userId", unique = false)
    ]
)
class UserTokenDb() {
    @Id
    var id: String = ""
    var userId: String = ""
    var type: String = ""
    var tokenHash: String = ""
    var createdAt: LocalDateTime = LocalDateTime.now(ZoneOffset.UTC)
    var expiresAt: LocalDateTime = LocalDateTime.now(ZoneOffset.UTC)

    fun toDomain(): UserToken = UserToken(id, userId, UserTokenType.valueOf(type), tokenHash, createdAt.toInstantUtc(), expiresAt.toInstantUtc())
}

fun UserToken.toDb(): UserTokenDb = UserTokenDb().also {
    it.id = id
    it.userId = userId
    it.type = type.name
    it.tokenHash = tokenHash
    it.createdAt = createdAt.toUtc()
    it.expiresAt = expiresAt.toUtc()
}

@Entity
@Table(
    name = "api_keys",
    indexes = [
        Index(name = "api_key_secret_hash", columnList = "secretHash", unique = true),
        Index(name = "api_key_user", columnList = "userId", unique = true)
    ]
)
class ApiKeyDb() {
    @Id
    var id: String = ""
    var name: String = ""
    var admin: Boolean = false

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "api_key_domain_roles", joinColumns = [JoinColumn(name = "api_key_id")])
    @MapKeyColumn(name = "domain_id")
    @Column(name = "role")
    var domains: MutableMap<String, String> = mutableMapOf()

    var secretHash: String = ""
    var prefix: String = ""
    var createdBy: String? = null
    var createdAt: LocalDateTime = LocalDateTime.now(ZoneOffset.UTC)
    var expiresAt: LocalDateTime? = null
    var lastUsedAt: LocalDateTime? = null
    var userId: String? = null

    fun toDomain(): ApiKey = ApiKey(
        id = id,
        name = name,
        admin = admin,
        domains = domains.mapValues { DomainRole.parse(it.value) },
        secretHash = secretHash,
        prefix = prefix,
        createdBy = createdBy,
        createdAt = createdAt.toInstantUtc(),
        expiresAt = expiresAt?.toInstantUtc(),
        lastUsedAt = lastUsedAt?.toInstantUtc(),
        userId = userId
    )
}

fun ApiKey.toDb(): ApiKeyDb = ApiKeyDb().also {
    it.id = id
    it.name = name
    it.admin = admin
    it.domains = domains.mapValues { d -> d.value.value }.toMutableMap()
    it.secretHash = secretHash
    it.prefix = prefix
    it.createdBy = createdBy
    it.createdAt = createdAt.toUtc()
    it.expiresAt = expiresAt?.toUtc()
    it.lastUsedAt = lastUsedAt?.toUtc()
    it.userId = userId
}
