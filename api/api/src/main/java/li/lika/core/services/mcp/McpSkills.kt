package li.lika.core.services.mcp

import li.lika.common.security.LikaPrincipal
import li.lika.core.props.McpProperties
import li.lika.core.services.AuthorizationService
import org.springframework.stereotype.Component

/**
 * A guide for AI assistants, in the Agent Skills format (SKILL.md: frontmatter, then markdown).
 */
data class Skill(val name: String, val description: String, val body: String) {
    val uri: String
        get() = "lika://skills/$name"

    val markdown: String
        get() = "---\nname: $name\ndescription: $description\n---\n\n${dedent(body).trim()}\n"

    companion object {
        /**
         * Removes the indentation of the source code, taken from the first line. Unlike trimIndent,
         * lines interpolated without indentation do not prevent it.
         */
        fun dedent(text: String): String {
            val lines = text.lines()
            val indent = lines.firstOrNull { it.isNotBlank() }?.takeWhile { it == ' ' }?.length ?: 0
            return lines.joinToString("\n") { line -> line.drop(minOf(indent, line.takeWhile { it == ' ' }.length)) }
        }
    }
}

/**
 * Skills of the MCP server, tailored to the caller: everyone gets the guide to links,
 * managers the guide to access, admins the guide to administration.
 */
@Component
class McpSkills(private val authorization: AuthorizationService, private val mcp: McpProperties) {

    fun forCaller(caller: LikaPrincipal): List<Skill> = buildList {
        if (Capabilities.reads(caller)) add(links(caller))
        if (Capabilities.manages(caller)) add(access(caller))
        if (Capabilities.administers(caller)) add(admin())
        add(restApi(caller))
    }

    private fun yourAccess(caller: LikaPrincipal): String {
        if (caller.admin) return "You are an **admin**: every right on every domain, plus domains, users, API keys and archives."
        val domains = authorization.accessibleDomains(caller)
        if (domains.isEmpty()) return "You have no role on any domain yet: ask an admin or a manager of a domain."
        return "Your roles (they can change; `list_domains` always tells the current ones):\n\n" +
            domains.joinToString("\n") { "- `${it.host}` (id `${it.id}`): **${caller.claims.roleOn(it.id!!)?.value}**" }
    }

    private fun links(caller: LikaPrincipal) = Skill(
        "lika-links",
        "Create, find, schedule, protect and clean up short links on Lika. Use when asked for a short link, a campaign link, or anything about existing links.",
        """
        # Short links on Lika

        A **domain** (e.g. `go.example.com`) serves **links**. A link maps a **slug** to a **target URL**: `https://go.example.com/summer-sale` redirects to the target.
        The full short URL is the link's `shortenedUrl`.

        ${yourAccess(caller)}

        ## Roles

        | Role | On its domain |
        |---|---|
        | viewer | lists, searches and reads links |
        | editor | also creates, updates and deletes links |
        | manager | also invites people and manages application API keys (see `lika-access`) |

        ## Tools

        - `list_domains`: domains you can use, with your role. Start here: link tools need a `domainId`.
        - `search_links`: paginated list, filtered by domain and text; sorted by `creation_date`, `last_activity` or `slug`.
        - `get_link`: by `id`, or by `domainId` and `slug`.
        - `suggest_slugs`: turns words into a slug and checks it is free; proposes alternatives when taken.
        - `create_link`, `update_link`, `delete_link`: editors only.

        ## Workflows

        **Create a link.** Pick the domain (ask when several fit). If the user wants a readable slug, call `suggest_slugs` with their words, then `create_link` with the free slug.
        Without a slug, Lika generates a random one. Give the user the returned `shortenedUrl`.

        **Schedule a link.** Set `activationInstant` (ISO-8601 with offset, e.g. `2026-06-01T09:00:00+02:00`). Until then, visitors go to `targetUrlBeforeActivation`,
        or get "not found" without it.

        **Make a link expire.** Set `expiryDate` (`YYYY-MM-DD`): the link works through that day, then is not found and is deleted the next night.

        **Protect a link.** Set `password`: visitors must type it. Lika never returns it; `options.password.enabled` tells whether a link is protected.
        To change a link without touching its password, omit `password` in `update_link`; `removePassword: true` drops the protection.

        **Label links.** `metadata` holds free text labels (e.g. `{"campaign": "summer"}`), searchable by the people managing the domain.

        ## Rules

        - Slugs are unique per domain, made of lowercase letters, digits and dashes. Some words are reserved.
        - Changing a slug or deleting a link breaks the short URL already shared: confirm with the user first.
        - A link belongs to its domain, not to its creator: any editor of the domain may change it.
        - Errors come back as tool errors with a message: "Invalid domain" means you have no role on it.
        """
    )

    private fun access(caller: LikaPrincipal) = Skill(
        "lika-access",
        "Invite people to Lika, set their roles per domain, and manage the API keys of applications. Use when asked to give someone access or to connect an application.",
        """
        # People and applications on Lika

        ${if (caller.admin) "As an admin, you manage everyone and every key." else "As a manager, you act on the domains you manage only, and never on admins:\n\n" + authorization.accessibleDomains(caller)
            .filter { caller.claims.roleOn(it.id!!)?.value == "manager" }.joinToString("\n") { "- `${it.host}` (id `${it.id}`)" }}

        ## Roles

        Each person has a role per domain: **viewer** (reads links), **editor** (also changes links), **manager** (also invites people and manages keys of the domain).
        Admins have every right everywhere. Rights apply immediately, even to open sessions.

        ## People

        - `list_users`: status (`invited`, `active`, `disabled`) and roles. Managers see their domains' part only.
        - `invite_user`: email, name and roles. Returns a single-use **invitation link**, valid 7 days. When `emailSent` is false, give the link to the user who asked,
          so they pass it on. Never post it publicly: whoever holds it joins as that person.
        - `set_user_access`: role per domain id; `null` removes it; domains not listed are unchanged.
        - `resend_invitation`: new link, the previous one stops working.

        ## Applications

        Applications (websites, scripts, other tools) call Lika with an **API key**, viewer or editor per domain.

        - `create_api_key`: returns the **secret once**. Give it to the user to store safely; it cannot be shown again.
        - `list_api_keys`, `update_api_key` (name, roles, expiry), `regenerate_api_key` (new secret, the old one stops working), `delete_api_key`.
        - Keys never manage domains or people. Prefer one key per application, with an expiry when it is temporary.

        People connecting an AI assistant use their own **user API key** instead (created in their profile in the web interface): it acts with their own rights.
        """
    )

    private fun admin() = Skill(
        "lika-admin",
        "Administer Lika: domains, people's lifecycle, admin keys and archives. Use when asked to add a domain, offboard someone, or back up Lika.",
        """
        # Administering Lika

        ## Domains

        - `create_domain`: `host` (e.g. `go.example.com`, its DNS pointing to the Lika API) and `shortUrlBase` (e.g. `https://go.example.com`). The host never changes.
        - `update_domain`: changing `shortUrlBase` rewrites every short URL of the domain.
        - `delete_domain`: refused while it has links, unless `force: true`, which deletes them. It also removes every role on it. Always confirm first.
        - A new domain has no users: give people roles with `set_user_access` or `invite_user`.

        ## People

        - `disable_user`: signs them out everywhere, their user API key included; `enable_user` restores their access. Prefer it to deletion when unsure.
        - `delete_user`: irreversible; their links stay. Lika always keeps one active admin.
        - `password_reset_link`: single-use link valid 24 hours, for someone who forgot their password. Give it to them privately.
        - `update_user`: name, or the email they sign in with.
        - `set_user_access` with `admin: true` makes someone admin. Do it only when explicitly asked.

        ## Keys and archives

        - Admin API keys (`create_api_key` with `admin: true`) have every right: only for trusted automation, such as backups.
        - `export_archive`: every user, key, domain and link as JSON, secrets and passwords hashed. Archives are imported in the web interface.
        """
    )

    private fun restApi(caller: LikaPrincipal): Skill {
        val base = mcp.publicUrl?.trimEnd('/')?.takeIf { it.isNotBlank() } ?: "https://<lika-api>"
        return Skill(
            "lika-rest-api",
            "Call the Lika REST API from an application with an API key. Use when writing code or scripts that create or read short links.",
            """
            # Lika REST API for applications

            Applications authenticate with an **API key** created by an admin or a domain manager: send `Authorization: Bearer <key>`
            (or `X-Api-Key: <key>`) and `Accept: application/json`. User API keys (`lika_u_…`) only work on the MCP server, not here.

            Base URL: `$base`. OpenAPI: `$base/v3/api-docs`, Swagger UI: `$base/swagger-ui.html`.

            ## Endpoints

            | | |
            |---|---|
            | `GET /api/domains` | domains the key has a role on |
            | `GET /api/links?domainId=&filter=&limit=&offset=&sort_by=&sort_direction=` | page of links (`totalItemCount`, `items`) |
            | `GET /api/links/{id}` | one link |
            | `GET /api/search/slug/{slug}?domainId=` | one link by slug |
            | `GET /api/search/slug/suggestions/{text}?domainId=` | free slugs |
            | `POST /api/links` | create (editor): `{"domainId", "targetUrl", "slug"?, "options"?, "metadata"?}` |
            | `PUT /api/links` | update (editor): same body with `id` |
            | `DELETE /api/links/{id}` | delete (editor) |

            `options`: `{"expiry": {"expiryDate": "2026-12-31"}, "activation": {"activationInstant": "…", "targetUrlBeforeActivation": "…"}, "password": {"password": "…"}}`.
            On update, omitting `password` keeps the current one; `{"remove": true}` drops it.

            ## Example

            ```bash
            curl -X POST $base/api/links \
              -H "Authorization: Bearer ${'$'}LIKA_API_KEY" -H "Content-Type: application/json" -H "Accept: application/json" \
              -d '{"domainId": "<domain id>", "targetUrl": "https://example.com/summer", "slug": "summer"}'
            ```

            Errors are JSON: `{"statusCode", "errorCode", "message"}`. 401: unknown, expired or revoked key. 404 on a domain: the key has no role on it.
            409: slug taken. 403: role too low (e.g. a viewer key writing).
            ${if (caller is LikaPrincipal.UserPrincipal) "\nTo get a key for an application, ask an admin or a manager of the domain, or create one with `create_api_key` if you manage it." else ""}
            """
        )
    }
}
