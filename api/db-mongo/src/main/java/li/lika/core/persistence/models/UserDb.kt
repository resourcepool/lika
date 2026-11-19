package li.lika.core.persistence.models

import li.lika.common.models.*
import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.index.Indexed
import org.springframework.data.mongodb.core.mapping.Document
import java.time.Instant

@Document(collection = "users")
data class UserDb(
    @Id val id: String = "",
    @Indexed(unique = true) val email: String = "",
    val name: String = "",
    val status: String = UserStatus.INVITED.name,
    val admin: Boolean = false,
    val domains: Map<String, String> = emptyMap(),
    val version: Long = 1,
    val passwordHash: String? = null,
    @Indexed val externalId: String? = null,
    val invitedBy: String? = null,
    val createdAt: Instant = Instant.now(),
    val lastLoginAt: Instant? = null,
    val sessionsNotBefore: Instant? = null
) {
    fun toDomain(): User = User(id, email, name, UserStatus.valueOf(status), admin, domains.mapValues { DomainRole.parse(it.value) },
        version, passwordHash, externalId, invitedBy, createdAt, lastLoginAt, sessionsNotBefore)

    companion object {
        fun from(u: User) = UserDb(u.id, u.email, u.name, u.status.name, u.admin, u.domains.mapValues { it.value.value },
            u.version, u.passwordHash, u.externalId, u.invitedBy, u.createdAt, u.lastLoginAt, u.sessionsNotBefore)
    }
}

@Document(collection = "user_tokens")
data class UserTokenDb(
    @Id val id: String = "",
    @Indexed val userId: String = "",
    val type: String = "",
    @Indexed(unique = true) val tokenHash: String = "",
    val createdAt: Instant = Instant.now(),
    val expiresAt: Instant = Instant.now()
) {
    fun toDomain() = UserToken(id, userId, UserTokenType.valueOf(type), tokenHash, createdAt, expiresAt)

    companion object {
        fun from(t: UserToken) = UserTokenDb(t.id, t.userId, t.type.name, t.tokenHash, t.createdAt, t.expiresAt)
    }
}

@Document(collection = "api_keys")
data class ApiKeyDb(
    @Id val id: String = "",
    val name: String = "",
    val admin: Boolean = false,
    val domains: Map<String, String> = emptyMap(),
    @Indexed(unique = true) val secretHash: String = "",
    val prefix: String = "",
    val createdBy: String? = null,
    val createdAt: Instant = Instant.now(),
    val expiresAt: Instant? = null,
    val lastUsedAt: Instant? = null,
    @Indexed(sparse = true) val userId: String? = null
) {
    fun toDomain() = ApiKey(id, name, admin, domains.mapValues { DomainRole.parse(it.value) }, secretHash, prefix, createdBy, createdAt, expiresAt, lastUsedAt, userId)

    companion object {
        fun from(k: ApiKey) = ApiKeyDb(k.id, k.name, k.admin, k.domains.mapValues { it.value.value }, k.secretHash, k.prefix, k.createdBy, k.createdAt, k.expiresAt, k.lastUsedAt, k.userId)
    }
}
