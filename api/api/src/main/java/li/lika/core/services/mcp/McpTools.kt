package li.lika.core.services.mcp

import com.fasterxml.jackson.databind.JsonNode
import li.lika.common.models.*
import li.lika.common.models.dtos.PageRequest
import li.lika.common.models.dtos.Sort
import li.lika.common.models.dtos.SortDirection
import li.lika.common.models.dtos.SortProperty
import li.lika.common.security.LikaPrincipal
import li.lika.core.services.ArchiveService
import li.lika.core.services.AuthorizationService
import li.lika.core.services.DeeplinkService
import li.lika.core.services.DomainService
import li.lika.core.services.auth.ApiKeyService
import li.lika.core.services.auth.UserService
import org.springframework.stereotype.Component
import java.time.Instant
import java.time.format.DateTimeFormatter

/**
 * A tool of the MCP server. [visibleTo] hides it from callers who could never use it;
 * the services still check every call, exactly as for the REST API.
 */
class McpTool(
    val name: String,
    val title: String,
    val description: String,
    val inputSchema: Map<String, Any>,
    val readOnly: Boolean,
    val destructive: Boolean = false,
    val visibleTo: (LikaPrincipal) -> Boolean,
    val call: (LikaPrincipal, JsonNode) -> Any?
) {
    fun definition(): Map<String, Any> = mapOf(
        "name" to name,
        "title" to title,
        "description" to description,
        "inputSchema" to inputSchema,
        "annotations" to mapOf(
            "title" to title,
            "readOnlyHint" to readOnly,
            "destructiveHint" to destructive,
            "idempotentHint" to (readOnly || destructive),
            "openWorldHint" to false
        )
    )
}

/** What a caller may do, to decide which tools and skills it sees */
object Capabilities {
    fun reads(p: LikaPrincipal) = p.admin || p.claims.domains.isNotEmpty()
    fun edits(p: LikaPrincipal) = p.admin || p.claims.domains.values.any { it.includes(DomainRole.EDITOR) }
    /** Users and API keys: admins, and people managing a domain (application keys never do) */
    fun manages(p: LikaPrincipal) = p.admin || (p is LikaPrincipal.UserPrincipal && p.claims.domains.values.any { it == DomainRole.MANAGER })
    fun administers(p: LikaPrincipal) = p.admin
}

@Component
class McpTools(
    private val links: DeeplinkService,
    private val domains: DomainService,
    private val authorization: AuthorizationService,
    private val users: UserService,
    private val keys: ApiKeyService,
    private val archive: ArchiveService,
    private val skills: McpSkills
) {
    val all: List<McpTool> = listOf(
        // --- Skills, for clients which do not read MCP resources
        McpTool(
            "list_skills", "List skills",
            "Lists the guides (skills) explaining how to use Lika, tailored to your rights. Read the relevant one with get_skill before acting.",
            obj(), readOnly = true, visibleTo = { true },
            call = { p, _ -> skills.forCaller(p).map { mapOf("name" to it.name, "description" to it.description, "uri" to it.uri) } }
        ),
        McpTool(
            "get_skill", "Read a skill",
            "Returns a guide (skill) as markdown: concepts, rules and workflows, tailored to your rights.",
            obj("name" to str("Skill name, from list_skills"), required = listOf("name")), readOnly = true, visibleTo = { true },
            call = { p, a -> skills.forCaller(p).firstOrNull { it.name == a.text("name") }?.markdown ?: throw IllegalArgumentException("Unknown skill: ${a.text("name")}") }
        ),

        // --- Domains and links
        McpTool(
            "list_domains", "List domains",
            "Lists the domains you have a role on, with your role. Links live on a domain: most link tools need its id.",
            obj(), readOnly = true, visibleTo = Capabilities::reads,
            call = { p, _ ->
                domains.getAll(p).map { d ->
                    mapOf("id" to d.id, "host" to d.host, "shortUrlBase" to d.apiBaseUrl, "landingBaseUrl" to d.landingBaseUrl, "standard" to d.standard,
                        "yourRole" to p.claims.roleOn(d.id!!)?.value)
                }
            }
        ),
        McpTool(
            "search_links", "Search links",
            "Lists links, newest first by default. Filter by domain and by text (matched against slug and target URL). Paginated: use offset to read further.",
            obj(
                "domainId" to str("Only this domain. Omit for every domain you can read."),
                "query" to str("Text to look for in slugs and target URLs"),
                "limit" to int("Page size, 1 to 50", 1, 50),
                "offset" to int("Index of the first link to return", 0, null),
                "sortBy" to str("Sort property", enum = listOf("creation_date", "last_activity", "slug")),
                "sortDirection" to str("Sort direction", enum = listOf("asc", "desc"))
            ),
            readOnly = true, visibleTo = Capabilities::reads,
            call = { p, a ->
                val req = PageRequest(
                    (a.int("limit") ?: 20).coerceIn(1, 50), (a.int("offset") ?: 0).coerceAtLeast(0), a.text("query"),
                    Sort.with(SortDirection.parse(a.text("sortDirection")), SortProperty.parse(a.text("sortBy")))
                )
                val page = links.getPage(p, a.text("domainId"), req)
                mapOf("totalItemCount" to page.totalItemCount, "offset" to req.offset, "items" to page.items, "hasMore" to page.hasNext())
            }
        ),
        McpTool(
            "get_link", "Get a link",
            "Returns one link, by id, or by domain and slug. Password-protected links never return their password.",
            obj("id" to str("Link id"), "domainId" to str("Domain id, with slug"), "slug" to str("Slug, with domainId")),
            readOnly = true, visibleTo = Capabilities::reads,
            call = { p, a ->
                val id = a.text("id")
                if (id != null) links.findOne(p, id)
                else links.findOneByDomainAndSlug(p, a.need("domainId"), a.need("slug"))
            }
        ),
        McpTool(
            "suggest_slugs", "Suggest slugs",
            "Turns a text into a slug and tells whether it is free on the domain. When taken, returns free alternatives.",
            obj("domainId" to str("Domain id"), "text" to str("Words to build the slug from, e.g. 'Summer sale 2026'"), required = listOf("domainId", "text")),
            readOnly = true, visibleTo = Capabilities::reads,
            call = { p, a -> mapOf("suggestions" to links.suggestSlugs(p, a.need("domainId"), a.need("text"))) }
        ),
        McpTool(
            "create_link", "Create a link",
            "Creates a short link on a domain (editor role needed). Without slug, a random one is generated. Returns the link with its shortenedUrl.",
            obj(
                "domainId" to str("Domain id, from list_domains"),
                "targetUrl" to str("Where the short link redirects, absolute URL", format = "uri"),
                "slug" to str("Path of the short link, e.g. 'summer-sale'. Letters, digits and dashes."),
                "expiryDate" to str("Last day the link works (YYYY-MM-DD). It is not found afterwards.", format = "date"),
                "activationInstant" to str("ISO-8601 instant from which the link redirects to targetUrl", format = "date-time"),
                "targetUrlBeforeActivation" to str("Where the link redirects before activationInstant. Without it, the link is not found until then."),
                "password" to str("Protects the link: visitors must type it"),
                "metadata" to stringMap("Free key-value labels, e.g. {\"campaign\": \"summer\"}"),
                required = listOf("domainId", "targetUrl")
            ),
            readOnly = false, visibleTo = Capabilities::edits,
            call = { p, a ->
                val options = OptionsDto(
                    expiry = a.text("expiryDate")?.let { ExpiryOptionsDto(it) },
                    activation = a.text("activationInstant")?.let { ActivationOptionsDto(it, a.text("targetUrlBeforeActivation")) },
                    password = a.text("password")?.let { PasswordProtectedOptionsDto(it) }
                )
                links.save(DeeplinkCreator(null, a.need("targetUrl"), a.need("domainId"), a.text("slug"), DeeplinkType.SIMPLE, options, a.stringMap("metadata") ?: emptyMap()), p)
            }
        ),
        McpTool(
            "update_link", "Update a link",
            "Changes a link (editor role needed). Only the fields given change. Changing the slug breaks the previous short URL.",
            obj(
                "id" to str("Link id"),
                "targetUrl" to str("New destination", format = "uri"),
                "slug" to str("New slug"),
                "expiryDate" to str("New last day (YYYY-MM-DD)", format = "date"),
                "removeExpiry" to bool("The link never expires"),
                "activationInstant" to str("New activation instant (ISO-8601)", format = "date-time"),
                "targetUrlBeforeActivation" to str("Destination before activation"),
                "removeActivation" to bool("The link is active right away"),
                "password" to str("New password"),
                "removePassword" to bool("Removes the password protection"),
                "metadata" to stringMap("Replaces all metadata"),
                required = listOf("id")
            ),
            readOnly = false, visibleTo = Capabilities::edits,
            call = { p, a ->
                val link = links.findOne(p, a.need("id"))
                val domain = authorization.assertCanHost(p, link.host, DomainRole.EDITOR)
                val expiry = when {
                    a.bool("removeExpiry") -> null
                    a.text("expiryDate") != null -> ExpiryOptionsDto(a.text("expiryDate"))
                    else -> link.options.expiry?.expiryDate?.let { ExpiryOptionsDto(it.format(DateTimeFormatter.ISO_LOCAL_DATE)) }
                }
                val current = link.options.activation
                val activation = when {
                    a.bool("removeActivation") -> null
                    a.text("activationInstant") != null -> ActivationOptionsDto(a.text("activationInstant"), a.text("targetUrlBeforeActivation") ?: current?.targetUrlBeforeActivation)
                    current?.activationInstant != null -> ActivationOptionsDto(current.activationInstant!!.format(DateTimeFormatter.ISO_ZONED_DATE_TIME), a.text("targetUrlBeforeActivation") ?: current.targetUrlBeforeActivation)
                    else -> null
                }
                val password = when {
                    a.bool("removePassword") -> PasswordProtectedOptionsDto(remove = true)
                    a.text("password") != null -> PasswordProtectedOptionsDto(a.text("password"))
                    else -> null // keeps the current one
                }
                val options = OptionsDto(activation, link.options.landing?.let { LandingOptionsDto(it.templateId) }, expiry, password)
                links.save(DeeplinkCreator(link.id, a.text("targetUrl") ?: link.targetUrl!!, domain.id!!, a.text("slug") ?: link.slug, link.type, options, a.stringMap("metadata") ?: link.metadata), p)
            }
        ),
        McpTool(
            "delete_link", "Delete a link",
            "Deletes a link (editor role needed). Its short URL stops working and its slug becomes free.",
            obj("id" to str("Link id"), required = listOf("id")),
            readOnly = false, destructive = true, visibleTo = Capabilities::edits,
            call = { p, a -> links.deleteOne(p, a.need("id")); mapOf("deleted" to a.need("id")) }
        ),

        // --- Users: admins, and managers for their domains
        McpTool(
            "list_users", "List users",
            "Lists users with their status and roles. Managers only see the users of the domains they manage, and their roles there.",
            obj("status" to str("Only this status", enum = listOf("invited", "active", "disabled")), "domainId" to str("Only users with a role on this domain")),
            readOnly = true, visibleTo = Capabilities::manages,
            call = { p, a -> users.list(p, a.text("status")?.let { UserStatus.valueOf(it.uppercase()) }, a.text("domainId")) }
        ),
        McpTool(
            "invite_user", "Invite a user",
            "Invites someone by email with roles per domain (managers: only on domains they manage, never admin). Returns the invitation link, valid once; it is also emailed when emails are enabled.",
            obj(
                "email" to str("Email of the person", format = "email"),
                "name" to str("Their name"),
                "admin" to bool("Every right on Lika (admins only)"),
                "domains" to roleMap("Role per domain id", allowManager = true),
                required = listOf("email")
            ),
            readOnly = false, visibleTo = Capabilities::manages,
            call = { p, a -> users.invite(p, InvitationRequest(a.need("email"), a.text("name") ?: "", a.bool("admin"), a.roles("domains"))) }
        ),
        McpTool(
            "set_user_access", "Set the access of a user",
            "Changes the roles of a user, immediately. Give a role per domain id, or null to remove it; domains not listed keep their role. Managers only change the domains they manage, and never admins.",
            obj(
                "userId" to str("User id"),
                "admin" to bool("Admins only: grants or removes every right"),
                "domains" to roleMap("Role per domain id; null removes the role", allowManager = true, nullable = true),
                required = listOf("userId")
            ),
            readOnly = false, visibleTo = Capabilities::manages,
            call = { p, a ->
                val domainsArg = a.arg("domains")
                val changes = HashMap<String, DomainRole?>()
                domainsArg?.fields()?.forEach { (id, role) -> changes[id] = if (role.isNull) null else DomainRole.parse(role.asText()) }
                users.updateAccess(p, a.need("userId"), AccessUpdate(a.arg("admin")?.takeIf { !it.isNull }?.asBoolean(), changes))
            }
        ),
        McpTool(
            "resend_invitation", "Resend an invitation",
            "Creates a new invitation link for an invited user; the previous link stops working.",
            obj("userId" to str("User id"), required = listOf("userId")),
            readOnly = false, visibleTo = Capabilities::manages,
            call = { p, a -> users.resendInvitation(p, a.need("userId")) }
        ),

        // --- Application API keys: admins, and managers for their domains
        McpTool(
            "list_api_keys", "List API keys",
            "Lists the API keys of applications you manage (never their secrets).",
            obj("domainId" to str("Only keys with a role on this domain")),
            readOnly = true, visibleTo = Capabilities::manages,
            call = { p, a -> keys.list(p, a.text("domainId")) }
        ),
        McpTool(
            "create_api_key", "Create an API key",
            "Creates an API key for an application, viewer or editor per domain (admins may create admin keys). The secret is returned once: hand it over securely.",
            obj(
                "name" to str("Application using the key"),
                "domains" to roleMap("Role per domain id: viewer or editor", allowManager = false),
                "admin" to bool("Every right (admins only)"),
                "expiresAt" to str("ISO-8601 instant after which the key stops working", format = "date-time"),
                required = listOf("name")
            ),
            readOnly = false, visibleTo = Capabilities::manages,
            call = { p, a -> keys.create(p, ApiKeyRequest(a.need("name"), a.bool("admin"), a.roles("domains"), a.text("expiresAt")?.let { Instant.parse(it) })) }
        ),
        McpTool(
            "update_api_key", "Update an API key",
            "Renames an API key, replaces its roles, or changes its expiry.",
            obj(
                "id" to str("Key id"),
                "name" to str("New name"),
                "domains" to roleMap("Replaces the roles: role per domain id", allowManager = false),
                "expiresAt" to str("New expiry (ISO-8601)", format = "date-time"),
                "removeExpiry" to bool("The key never expires"),
                required = listOf("id")
            ),
            readOnly = false, visibleTo = Capabilities::manages,
            call = { p, a ->
                keys.update(p, a.need("id"), ApiKeyUpdate(a.text("name"), a.arg("domains")?.let { a.roles("domains") }, a.text("expiresAt")?.let { Instant.parse(it) }, a.bool("removeExpiry")))
            }
        ),
        McpTool(
            "regenerate_api_key", "Regenerate an API key",
            "Replaces the secret of a key; the previous one stops working immediately. The new secret is returned once.",
            obj("id" to str("Key id"), required = listOf("id")),
            readOnly = false, destructive = true, visibleTo = Capabilities::manages,
            call = { p, a -> keys.regenerate(p, a.need("id")) }
        ),
        McpTool(
            "delete_api_key", "Delete an API key",
            "Deletes a key; the application using it is refused immediately. Its links are kept.",
            obj("id" to str("Key id"), required = listOf("id")),
            readOnly = false, destructive = true, visibleTo = Capabilities::manages,
            call = { p, a -> keys.delete(p, a.need("id")); mapOf("deleted" to a.need("id")) }
        ),

        // --- Admins only
        McpTool(
            "create_domain", "Create a domain",
            "Registers a host serving short links. Its DNS must point to the Lika API. The host cannot change later.",
            obj(
                "host" to str("Host name, optionally with a port, e.g. go.example.com"),
                "shortUrlBase" to str("Base of the short URLs, e.g. https://go.example.com", format = "uri"),
                "landingBaseUrl" to str("Optional home page of the domain", format = "uri"),
                "standard" to bool("Shown as a system-wide domain"),
                required = listOf("host", "shortUrlBase")
            ),
            readOnly = false, visibleTo = Capabilities::administers,
            call = { p, a ->
                authorization.assertAdmin(p)
                domains.create(DomainCreator(a.need("host"), a.need("shortUrlBase"), a.text("landingBaseUrl") ?: ""), a.bool("standard"))
            }
        ),
        McpTool(
            "update_domain", "Update a domain",
            "Changes the short URL base (rewriting every short URL of the domain) or the landing URL. The host cannot change.",
            obj("id" to str("Domain id"), "shortUrlBase" to str("New base of the short URLs", format = "uri"), "landingBaseUrl" to str("New landing URL; empty removes it"), required = listOf("id")),
            readOnly = false, visibleTo = Capabilities::administers,
            call = { p, a -> authorization.assertAdmin(p); domains.update(a.need("id"), DomainUpdate(a.text("shortUrlBase"), a.text("landingBaseUrl"))) }
        ),
        McpTool(
            "delete_domain", "Delete a domain",
            "Deletes a domain and every role on it. Refused if it has links, unless force is true, which deletes its links too. Irreversible: confirm with the user first.",
            obj("id" to str("Domain id"), "force" to bool("Also delete its links"), required = listOf("id")),
            readOnly = false, destructive = true, visibleTo = Capabilities::administers,
            call = { p, a -> authorization.assertAdmin(p); domains.deleteOne(a.need("id"), a.bool("force")); mapOf("deleted" to a.need("id")) }
        ),
        McpTool(
            "update_user", "Update a user",
            "Changes the name or the sign-in email of a user.",
            obj("userId" to str("User id"), "name" to str("New name"), "email" to str("New email", format = "email"), required = listOf("userId")),
            readOnly = false, visibleTo = Capabilities::administers,
            call = { p, a -> users.update(p, a.need("userId"), UserUpdate(a.text("name"), a.text("email"))) }
        ),
        McpTool(
            "disable_user", "Disable a user",
            "Signs a user out everywhere and prevents them from signing in, their user API key included. Reversible with enable_user.",
            obj("userId" to str("User id"), required = listOf("userId")),
            readOnly = false, destructive = true, visibleTo = Capabilities::administers,
            call = { p, a -> users.disable(p, a.need("userId")) }
        ),
        McpTool(
            "enable_user", "Enable a user",
            "Lets a disabled user sign in again, with their previous access.",
            obj("userId" to str("User id"), required = listOf("userId")),
            readOnly = false, visibleTo = Capabilities::administers,
            call = { p, a -> users.enable(p, a.need("userId")) }
        ),
        McpTool(
            "delete_user", "Delete a user",
            "Deletes a user and their user API key. Their links are kept. Irreversible: confirm with the user first.",
            obj("userId" to str("User id"), required = listOf("userId")),
            readOnly = false, destructive = true, visibleTo = Capabilities::administers,
            call = { p, a -> users.delete(p, a.need("userId")); mapOf("deleted" to a.need("userId")) }
        ),
        McpTool(
            "password_reset_link", "Password reset link",
            "Creates a password reset link for an active user, valid once. It is also emailed when emails are enabled.",
            obj("userId" to str("User id"), required = listOf("userId")),
            readOnly = false, visibleTo = Capabilities::administers,
            call = { p, a -> users.passwordResetLink(p, a.need("userId")) }
        ),
        McpTool(
            "export_archive", "Export an archive",
            "Exports every user, API key, domain and link as a JSON archive (secrets hashed), for backups or migrations. Can be large.",
            obj(), readOnly = true, visibleTo = Capabilities::administers,
            call = { p, _ -> authorization.assertAdmin(p); archive.exportArchive() }
        )
    )

    fun visibleTo(caller: LikaPrincipal): List<McpTool> = all.filter { it.visibleTo(caller) }

    // --- JSON schema helpers

    private fun obj(vararg properties: Pair<String, Map<String, Any>>, required: List<String> = emptyList()): Map<String, Any> =
        buildMap {
            put("type", "object")
            put("properties", linkedMapOf(*properties))
            if (required.isNotEmpty()) put("required", required)
            put("additionalProperties", false)
        }

    private fun str(description: String, enum: List<String>? = null, format: String? = null): Map<String, Any> = buildMap {
        put("type", "string")
        put("description", description)
        enum?.let { put("enum", it) }
        format?.let { put("format", it) }
    }

    private fun int(description: String, min: Int?, max: Int?): Map<String, Any> = buildMap {
        put("type", "integer")
        put("description", description)
        min?.let { put("minimum", it) }
        max?.let { put("maximum", it) }
    }

    private fun bool(description: String): Map<String, Any> = mapOf("type" to "boolean", "description" to description)

    private fun stringMap(description: String): Map<String, Any> =
        mapOf("type" to "object", "description" to description, "additionalProperties" to mapOf("type" to "string"))

    private fun roleMap(description: String, allowManager: Boolean, nullable: Boolean = false): Map<String, Any> {
        val roles = if (allowManager) listOf("viewer", "editor", "manager") else listOf("viewer", "editor")
        val value: Map<String, Any> = if (nullable) mapOf("type" to listOf("string", "null"), "enum" to roles + null)
                                      else mapOf("type" to "string", "enum" to roles)
        return mapOf("type" to "object", "description" to description, "additionalProperties" to value)
    }

    // --- Argument helpers

    private fun JsonNode.arg(name: String): JsonNode? = this.path(name).takeIf { !it.isMissingNode }

    private fun JsonNode.text(name: String): String? = arg(name)?.takeIf { !it.isNull }?.asText()?.takeIf { it.isNotBlank() }

    private fun JsonNode.need(name: String): String = text(name) ?: throw IllegalArgumentException("$name is required")

    private fun JsonNode.int(name: String): Int? = arg(name)?.takeIf { it.canConvertToInt() }?.asInt()

    private fun JsonNode.bool(name: String): Boolean = arg(name)?.asBoolean(false) ?: false

    private fun JsonNode.stringMap(name: String): Map<String, String>? =
        arg(name)?.takeIf { it.isObject }?.let { node -> node.fields().asSequence().associate { it.key to it.value.asText() } }

    private fun JsonNode.roles(name: String): Map<String, DomainRole> =
        arg(name)?.takeIf { it.isObject }?.fields()?.asSequence()?.associate { it.key to DomainRole.parse(it.value.asText()) } ?: emptyMap()
}
