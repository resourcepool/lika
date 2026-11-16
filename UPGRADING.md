# Upgrading Lika

## 0.14.0 → 0.14.1

- MCP skills follow the Skills over MCP extension: Claude and other clients supporting it now get them with the connection (`skills/list`). Their URIs change from `lika://skills/<name>` to `skill://<name>/SKILL.md`; the `list_skills` and `get_skill` tools are unchanged.

## 0.13.x → 0.14.0

- Published images are multi-arch, amd64 and arm64: the same tags work on ARM servers (Graviton, Ampere, Raspberry Pi 64-bit, Apple silicon). See [Prebuilt images](README.md#prebuilt-images).
- API keys can be sent as `X-Api-Key: <key>` as well as `Authorization: Bearer <key>`, on the REST API and the MCP server. The Claude Desktop configuration in the profile uses it: no environment variable needed by `mcp-remote` anymore. See [docs/MCP.md](docs/MCP.md#claude-desktop).

## 0.13.0 → 0.13.1

- Images are published to the GitLab container registry of the project for each release, built from `api/Dockerfile` and `web/Dockerfile`: `api:vX.Y.Z-<db>` now also exists for PostgreSQL (`-postgres`), and `latest-<db>`, `latest` (API, SQLite) and `www:latest` follow the last release. See [Prebuilt images](README.md#prebuilt-images).
- Creation and update dates of links are kept to the millisecond: a link returned when created now has the same dates as when read back.

## 0.12.x → 0.13.0

- `LIKA_CLAIM_NAME` (default `lika`) renames the claim carrying the rights with Keycloak and Firebase, and the Keycloak user attribute, when another application already uses `lika`. Changing it updates the Keycloak mapper on start; the claim is rewritten at each user's next sign-in or token renewal. See [docs/auth](docs/auth/).
- Firebase: Lika now keeps the custom claims other applications wrote on an account. It used to replace them all with its own, and to erase them when a user was deleted in Lika.

## 0.11.x → 0.12.0

- Links not found are no longer logged by default. `LOGGING_LEVEL_LI_LIKA_NOTFOUND=DEBUG` logs why, with the slug, the domain looked up (host and id) and the headers it came from: useful behind a CDN. See [Behind a CDN](docs/CONFIGURATION.md#behind-a-cdn).

## 0.10.x → 0.11.0

- Behind a CDN rewriting the `Host` header to the origin, set `LIKA_HOST_HEADER` to a header the CDN fills with the original host (e.g. `X-Lika-Host`): slugs are looked up on that domain. See [Behind a CDN](docs/CONFIGURATION.md#behind-a-cdn).
- `X-Forwarded-Host` holding several hosts now resolves to the first one, the client's, instead of matching no domain.
- The search of a link by host (`GET /api/search/slug/{slug}` without `domainId`) reads the host like public links, `X-Forwarded-Host` included.

## 0.9.x → 0.10.0

- `LIKA_FIREBASE_SERVICE_ACCOUNT` takes the service account key file encoded in base64 (`base64 -w0 key.json`), easier to paste in a hosting panel. The JSON itself and the path to the file still work.

## 0.8.x → 0.9.0

- **PostgreSQL** is supported: build argument `DB=postgres` of `api/Dockerfile` (Gradle `-PdbPostgres`), with `DB_POSTGRES_URL`. Tables are created on start. To move existing data, export an archive from **Import / Export** and import it into the new instance.
- `api/.env.example` and `web/.env.example` list the variables of each image; the root `.env.example` is gone. `docker-compose.yml` now reads `api/.env` and `web/.env`, and publishes the web interface on port 3000 and the API on port 8080.
- [docs/DOKPLOY.md](docs/DOKPLOY.md) deploys the API and the web interface as two Dokploy applications.

## 0.7.x → 0.8.0

Nothing to migrate. New ways to deploy:

- `docker-compose.yml` at the root builds and runs the API and the web interface from source, configured by a `.env` file (see `.env.example`). It is made for [Dokploy](docs/DOKPLOY.md), and works with `docker compose up -d --build` anywhere.
- `api/Dockerfile` builds the API from source, choosing the database with the build argument `DB` (`sqlite`, `mongo` or `memory`). The image runs as an unprivileged user, keeps SQLite in `/data`, and has a health check. `api/api/Dockerfile`, which packages a jar built beforehand, is unchanged.

## 0.6.x → 0.7.0

Lika now has **users**. People sign in to the web interface with an email and a password; **API keys are for applications** only. Rights are claims carried by the user's token: admin, or a role per domain.

| Role | On its domain |
|---|---|
| `viewer` | reads and searches links |
| `editor` | also creates, edits and deletes links |
| `manager` | also invites users to the domain, sets their role there, and manages the API keys of the domain (users only) |
| admin | everything: domains, users, keys, archives |

### First start

The API migrates existing data on first start:

- Each account becomes API keys **keeping its token values**, so applications keep working: the read-write token of a global account becomes an admin key; the one of another account an `editor` key on its granted and owned domains; the read token a `viewer` key on the same domains (every domain for a global account). Accounts are then deleted. Domains have no owner anymore.
- `security.default-global-access-token` is **deprecated**: it becomes the admin API key named after `security.default-global-name`. Remove it once you have created keys in the web interface.
- Archives of 0.6 and before still import: their accounts become keys the same way.
- Upgrading from 0.4 or older: start 0.6 once first, so that standard domains are granted to your accounts.

Then create your admin account: as long as no admin exists, the API prints a **setup token** in its logs. Open the web interface, which shows the setup page, and paste it there. Invite everyone else from **Users**.

### Configuration of the `api` image

New variables, all described in [docs/CONFIGURATION.md](docs/CONFIGURATION.md):

- `LIKA_WEB_URL` (**set it in production**): public URL of the web interface, used in invitation and password reset links.
- `LIKA_AUTH_PROVIDER`: `local` (default), `keycloak` or `firebase`, with their `LIKA_JWT_SECRET`, `LIKA_KEYCLOAK_*` or `LIKA_FIREBASE_*` variables.
- `LIKA_SETUP_TOKEN`: fixed setup token, for automated installs.
- `LIKA_PUBLIC_API_URL`, `LIKA_MCP_ALLOWED_ORIGINS`: MCP server.
- `MAILJET_API_KEY`, `MAILJET_API_SECRET`, `MAIL_FROM`, `MAIL_FROM_NAME`: emails.

The `www` image needs no new variable.

The development defaults (sample domains and links, root key `lika-client-secret`) moved to the `dev` profile (`application-dev.yml`). Before 0.7, they applied whenever no profile was active, production included. Images now start without them:

- An account holding the public token `lika-client-secret` or `lika-client-read` is not migrated to an API key. If you really use these values, set them explicitly with `SECURITY_DEFAULTGLOBALACCESSTOKEN`, then replace them with real keys.
- The standard domains `localhost:8080` and `127.0.0.1:8080` stay in the database if they were created; delete them in **Domains** if you do not use them.
- To run the jar with the development defaults: `SPRING_PROFILES_ACTIVE=dev`.

### Identity providers

With `LIKA_AUTH_PROVIDER=keycloak` or `firebase`, credentials stay at the provider, and Lika keeps users, invitations and rights:

- The first admin is created with the setup token, as with `local`. An account the provider already has for that email is linked; otherwise it is created with the password typed.
- Invitations work the same. Someone who already has an account at the provider just accepts, then signs in with it. Accounts are also linked when their owner signs in with a verified email matching an invitation. Nobody gets in without an invitation.
- Rights are written to the provider as the `lika` claim of the tokens (Keycloak user attribute and protocol mapper, Firebase custom claim).
- Disabling a user, signing them out everywhere, or deleting them only affects Lika: their account at the provider, possibly shared with other applications, stays enabled. Deletion only removes the `lika` claim.
- Keycloak: sign-in on the Keycloak page, passwords managed in Keycloak. Firebase: email and password typed in Lika, and Google when configured.

### MCP server and user API keys

The API serves an MCP server at `/mcp`, for AI assistants. It authenticates like the REST API, plus **user API keys**: each person can create one in their profile. It acts with their current rights and only works on `/mcp`. Tools and skills match the caller's rights. See [docs/MCP.md](docs/MCP.md).

### Web interface

- Sign-in with email and password; the API-key sign-in is gone, and browsers signed in with a key in 0.6 are signed out.
- New pages: setup, invitation, forgotten and reset password, profile (name, password, user API key and MCP setup, sign out everywhere).
- With Keycloak or Google, "Sign in with …" goes through the provider (`/auth/sso`, `/auth/callback`). Add `<web URL>/auth/callback` to the redirect URIs of the client.
- **Users** and **API Keys** are under *Manage*, for admins and managers. `/admin/keys` redirects to `/keys`.

## 0.5.x → 0.6.0

The web interface is rebuilt with Next.js. The image keeps its name (`<registry>/<project>/www`), its port (80) and its `/health` endpoint, but it is now a Node.js server instead of static files served by nginx: the browser no longer calls the API directly.

### Configuration of the `www` image

| Before | Now |
|---|---|
| `API_BASE_URL`: API URL as seen **by the browser** | `LIKA_API_URL`: API URL as seen **by the web container**, e.g. `http://lika-api:8080` on a Docker network. `API_BASE_URL` is still read as a fallback, with a deprecation warning. |
| `ENVIRONMENT_NAME`, `PRODUCTION`, `VERSION` | Not used anymore |
| — | `LIKA_SESSION_SECRET` (**required**): random string of at least 32 characters, e.g. `openssl rand -base64 48`. Keep it stable: changing it signs everyone out. |

The container exits on startup if `LIKA_SESSION_SECRET` or `LIKA_API_URL` is missing.

```yaml
# docker-compose example
services:
  api:
    image: <registry>/<project>/api:v0.6.0-sqlite
  www:
    image: <registry>/<project>/www:v0.6.0
    ports: ["8081:80"]
    environment:
      LIKA_API_URL: http://api:8080
      LIKA_SESSION_SECRET: ${LIKA_SESSION_SECRET}
```

### Behavior

- Sign-in takes the read-write API key once. It is kept in an encrypted, httpOnly cookie and never exposed to browser JavaScript. The session lasts as long as the browser visits at least once every 400 days, until sign-out, or until the key is regenerated or deleted.
- Behind a TLS-terminating reverse proxy, forward `X-Forwarded-Proto` so the cookie is marked `Secure`.
- The API no longer needs CORS for the web interface: only the web container calls it.
- The GitLab Pages deployment of the web interface is removed: it needs a server now.
- The password and "link not found" pages served by the API share the new look.

## 0.4.x → 0.5.0

The API migrates existing data on first start. Nothing to do by hand, but read the behavior changes below.

### Automatic migrations (SQLite, MongoDB)

- **Domain grants.** Before 0.5.0, every API key could manage the links of every standard domain (the ones listed under `lika.domains` in the configuration). Keys created by an older version are granted those domains once, so they keep the same access after the upgrade. Keys created afterwards start with no grant.
- **Link passwords** stored in clear text are replaced by their BCrypt hash.

### Access model

- A **global** key administers everything: all domains and `/api/admin/**`. Creating an account with `global: true` now works (the flag was ignored before).
- Any other key manages the links of the domains it **owns** or was **granted** (`PUT /api/admin/accounts/{id}/domains`). Its read-write token manages those links; its read token can only read them through `/api/search/**`.
- Standard domains are no longer open to every key: grant them explicitly.
- Every key granted on a domain manages **all** its links, whoever created them. `Deeplink.ownerId` is renamed `createdBy`.
- Links of other domains are reported as not found (404), including by id.

### API changes

| Endpoint | Change |
|---|---|
| `POST /api/admin/accounts` | honors `global`, accepts `domainIds` |
| `PATCH /api/admin/accounts/{id}` | new: rename (`{name}`) |
| `POST /api/admin/accounts/{id}/regenerate` | new: `{token: "read_write" \| "read"}`; the old token stops working immediately |
| `PUT /api/admin/accounts/{id}/domains` | new: replaces the domains granted to the key |
| `DELETE /api/admin/accounts/{id}` | 409 when deleting the last global account |
| `PATCH /api/admin/domains/{id}` | new: updates `apiBaseUrl` / `landingBaseUrl` (the host cannot change) and the short URLs of its links |
| `DELETE /api/admin/domains/{id}` | 409 if the domain still has links, unless `?force=true`, which deletes them too; grants on the domain are removed |
| `GET /api/links`, `/api/admin/links` | `domainId` is optional: without it, lists the links of every accessible domain |
| `GET /api/domains` | returns owned and granted domains (all domains for a global key) |
| `POST /api/admin/archive/export` | exports archive v2 (see below) |
| `POST /api/admin/archive/import` | reads v1 and v2 archives; `?dryRun=true` reports without writing; `?conflict=skip\|overwrite` (default `skip`); returns a report |

`/api/admin/links/**` is kept as an alias of `/api/links/**`, restricted to admins.

### Passwords

- Link responses never contain the password: `options.password` is `{"enabled": true}` on protected links.
- When editing a link, **omitting `options.password` keeps the current password**. Send `{"password": "..."}` to replace it or `{"remove": true}` to remove the protection.
- The password page of a protected link posts the password to the server, which redirects on success. It no longer embeds any token.

### Link behavior

- Before its activation date, a link redirects to `targetUrlBeforeActivation`, or is not found if there is none.
- A link is not found as soon as its expiry date has passed (it used to work until the next nightly cleanup). The cleanup now also runs on MongoDB, where it never matched any link.
- Editing a link keeps its creation date.

### Archive v2

Archives now hold `"version": 2`, a top-level `domains` list (with their `owner` and links), and per account the `grants` (domain hosts). Links keep their metadata, password hash, creator and timestamps. Version 1 archives still import: their non-global accounts are granted the standard domains, as in the migration above.

Exports contain every API key in clear text: store them accordingly.
