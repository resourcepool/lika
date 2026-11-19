# Lika MCP server

Lika exposes a [Model Context Protocol](https://modelcontextprotocol.io) server, so that AI assistants (Claude, Cursor, VS Code, any MCP client) can create and manage short links, and, depending on your rights, people, API keys and domains.

- **Endpoint**: `https://<lika-api>/mcp`. Your profile in the web interface shows the exact URL.
- **Transport**: Streamable HTTP, stateless. Each JSON-RPC request is a `POST` answered with JSON. There is no server-initiated stream, and no session.
- **Protocol versions**: `2026-07-28` (with `server/discover`), `2025-11-25`, `2025-06-18`, `2025-03-26`.
- **Same rights as everywhere**: tools, skills and every call follow the rights of the credentials used, exactly as the REST API does.

## Authentication

Send `Authorization: Bearer <credential>` with every request. API keys can also be sent as `X-Api-Key: <key>`, simpler for clients that split header values on spaces (Claude Desktop through `mcp-remote`). `Authorization` wins when both are sent. Three kinds of credentials are accepted:

| Credential | Created in | Acts with | REST API | MCP |
|---|---|---|---|---|
| **User API key** (`lika_u_…`) | your **Profile** › *AI assistants (MCP)* | your **current** rights: when they change, the key follows. Disabled user: refused | no | yes |
| **Application API key** (`lika_…`) | **API Keys** (admins and domain managers) | its own roles: viewer or editor per domain, or admin | yes | yes |
| User access token (JWT) | sign-in | the user's rights | yes | yes |

Use a **user API key** to connect your own assistant. Create one per application (Claude Desktop, Cursor…), named after it, up to 20: your profile lists them with when each was last used, and revoking one leaves the others connected. An admin can revoke them all from **Users**. Deleting the user deletes their keys.

Use an **application API key** for an assistant or an automation that works on behalf of a team rather than a person.

Without valid credentials, `/mcp` answers `401` with a `WWW-Authenticate: Bearer` header.

> Lika does not implement OAuth. Clients that can only connect through an OAuth flow are not supported, e.g. custom connectors added in the claude.ai web app. Clients accepting a header are.

## Connecting a client

Replace `https://go.example.com/mcp` with your URL and `lika_u_…` with your user API key. Your profile generates these snippets with your URL and key already filled in.

### Claude Code

```bash
claude mcp add --transport http lika https://go.example.com/mcp \
  --header "Authorization: Bearer lika_u_…"
```

Add `--scope user` to use it in every project. `/mcp` in Claude Code shows the connection and its tools.

### Claude Desktop

Claude Desktop reaches remote servers through [`mcp-remote`](https://www.npmjs.com/package/mcp-remote), which needs Node.js. In *Settings › Developer › Edit Config* (`claude_desktop_config.json`):

```json
{
  "mcpServers": {
    "lika": {
      "command": "npx",
      "args": ["-y", "mcp-remote", "https://go.example.com/mcp", "--header", "X-Api-Key:lika_u_…"]
    }
  }
}
```

`mcp-remote` splits arguments on spaces: `X-Api-Key` has none, unlike `Authorization: Bearer …`.

### Cursor

`~/.cursor/mcp.json`, or `.cursor/mcp.json` in a project:

```json
{
  "mcpServers": {
    "lika": {
      "url": "https://go.example.com/mcp",
      "headers": { "Authorization": "Bearer lika_u_…" }
    }
  }
}
```

### VS Code

`.vscode/mcp.json`, or the *MCP: Add Server* command:

```json
{
  "servers": {
    "lika": {
      "type": "http",
      "url": "https://go.example.com/mcp",
      "headers": { "Authorization": "Bearer lika_u_…" }
    }
  }
}
```

VS Code can also prompt for the key instead of storing it, with an `inputs` entry of type `promptString` (`"password": true`) referenced as `${input:lika-key}`.

### Other clients and checks

Any client supporting Streamable HTTP with custom headers works. To check a connection:

```bash
curl -s https://go.example.com/mcp \
  -H "Authorization: Bearer lika_u_…" \
  -H "Content-Type: application/json" -H "Accept: application/json, text/event-stream" \
  -d '{"jsonrpc":"2.0","id":1,"method":"tools/list"}'
```

The [MCP Inspector](https://github.com/modelcontextprotocol/inspector) lets you browse the tools and skills interactively:

```bash
npx @modelcontextprotocol/inspector --cli https://go.example.com/mcp --transport http \
  --header "Authorization: Bearer lika_u_…" --method tools/list
```

## Tools

`tools/list` only returns the tools the caller may use. The services still check each call, domain by domain.

| Tool | Who sees it | Does |
|---|---|---|
| `list_skills`, `get_skill` | everyone | the skills below, for clients that do not read MCP resources |
| `list_domains` | any role | domains you can use, with your role on each |
| `search_links` | any role | paginated links, filtered by domain and text, sorted |
| `get_link` | any role | one link, by id or by domain and slug |
| `suggest_slugs` | any role | slug from words, with free alternatives |
| `create_link`, `update_link` | editors | with expiry, scheduled activation, password, metadata. `update_link` only changes the fields given |
| `delete_link` | editors | |
| `list_users`, `invite_user`, `set_user_access`, `resend_invitation` | admins, **people** managing a domain | managers act on their domains only, and never on admins |
| `list_api_keys`, `create_api_key`, `update_api_key`, `regenerate_api_key`, `delete_api_key` | admins, people managing a domain | application keys only. Secrets are returned once |
| `create_domain`, `update_domain`, `delete_domain` | admins | |
| `update_user`, `disable_user`, `enable_user`, `delete_user`, `password_reset_link` | admins | |
| `export_archive` | admins | every user, key, domain and link as JSON (secrets hashed) |

"People managing a domain" means users, or their user API keys. Application keys, which are viewer or editor, never manage. An admin application key sees every tool.

Tools carry MCP annotations (`readOnlyHint`, `destructiveHint`), so that clients ask for confirmation before destructive calls.

Results come as JSON text content, plus `structuredContent`; lists are wrapped as `{"items": [...]}`. Business errors are tool results with `isError: true` and a message ending with its error code, which the model can act on: `slug-already-exists`, `invalid-domain` (unknown domain, or no role on it), `resource-access-forbidden` (role too low), `last-admin`, etc. An unknown tool, or a tool you may not use, is a JSON-RPC error `-32602`.

## Skills

The server publishes guides in the Agent Skills format (`SKILL.md`: YAML frontmatter, then markdown). They explain concepts, rules and workflows to the model. Like the tools, they depend on the caller: they list your domains and your roles, and only describe what you may do.

| Skill | For | Covers |
|---|---|---|
| `lika-links` | any role | domains, slugs, scheduling, expiry, passwords, metadata, rules |
| `lika-access` | admins, people managing a domain | roles, invitations, access changes, application keys |
| `lika-admin` | admins | domains, people's lifecycle, admin keys, archives |
| `lika-rest-api` | everyone | calling the REST API from an application with a key |

They follow the [Skills over MCP](https://modelcontextprotocol.io/extensions/skills/overview) extension (`io.modelcontextprotocol/skills`), declared in the capabilities of `initialize` and `server/discover`: clients supporting it list them with `skills/list` and offer them with the connection. Each skill is `skill://<name>/SKILL.md` (`text/markdown`), with the SHA-256 digest and size of the file in its entry; `skills/get` returns one entry by URI.

For other clients, they are also plain MCP **resources** (`resources/list`, `resources/read`), and available through the `list_skills` and `get_skill` tools. The server's instructions point the model to them.

To install them as local skills in an assistant that supports Agent Skills, save each one as `<name>/SKILL.md`. They reflect your rights at the time you save them.

### Claude plugin

Claude (claude.ai, Claude Desktop, Cowork, Claude Code) does not read skills from MCP servers: it takes them from **plugins**. **Profile › AI assistants › Download plugin** generates one, `lika-claude-plugin.zip` (`GET /api/me/claude-plugin`):

```
lika/
├── .claude-plugin/plugin.json
├── .mcp.json          the MCP server, X-Api-Key read from ${LIKA_API_KEY}
├── README.md
└── skills/<name>/SKILL.md
```

- **claude.ai, Claude Desktop, Cowork**: *Customize › Plugins › Add › Upload plugin*, then pick the zip. The skills load at once. The connector on the plugin's *Connectors* tab needs OAuth, which Lika does not implement: keep connecting the server as in [Claude Desktop](#claude-desktop).
- **Claude Code**: unzip it, set `LIKA_API_KEY` to your user API key, and run `claude --plugin-dir ./lika`, or add it from a marketplace of yours. The plugin connects to the server itself.

The plugin holds no secret. Its skills follow your rights when you download it, and its version changes each time: download it again when your rights change. Without `LIKA_PUBLIC_API_URL`, it points to the MCP URL your profile shows.

## Server configuration

- `LIKA_PUBLIC_API_URL`: public URL of the API. It gives the MCP URL shown in the web interface (`<url>/mcp`) and the one used in the skills. Without it, the web interface shows the URL it uses itself, which may be internal.
- `LIKA_MCP_ALLOWED_ORIGINS`: origins of browser-based MCP clients. Requests from other origins are refused, which protects against DNS rebinding. Desktop and server clients send no origin and are always accepted.

See [CONFIGURATION.md](CONFIGURATION.md). `/mcp` is served by the API, next to the short links: any domain pointing to the API serves it. Behind a reverse proxy, forward `POST /mcp` with its `Authorization` header, and do not buffer or cache it.

User API keys work the same with every identity provider (`LIKA_AUTH_PROVIDER`): they belong to Lika, not to the provider.

## Security

- A user API key holds your rights: store it like a password, in your client's secret storage where possible, never in a shared repository. One key per application keeps a leak small: revoke that key as soon as it leaks, and create another.
- Give assistants working for a team an application key limited to the domains and the role they need, with an expiry.
- Destructive tools (`delete_*`, `disable_user`, `regenerate_api_key`) are annotated as such. Keep your client's confirmation prompts on for them.
- Invitation and password reset links returned by the tools let anyone holding them sign in as that person: they should only be passed on privately.

## Limitations

- No OAuth: clients must send a bearer header.
- No server-to-client stream (`GET /mcp` answers `405`): Lika never sends notifications. Tool and resource lists are fixed for given rights (`listChanged: false`). After your rights change, reconnect the client to see new tools.
- No prompts, no resource subscriptions.
- Large exports (`export_archive`) are returned in a single response.
