# Lika

Lika is an open-source permalink service: short links on your own domains, managed from a web interface, a REST API, or an AI assistant through MCP.

`https://go.example.com/spring-sale` → `https://shop.example.com/campaigns/2026/spring?utm_source=go`

## Features

- **Short links on your own domains**: one Lika serves as many domains as you register (`go.example.com`, `taki.li`…). Each slug lives on its domain.
- **Link options**
  - *activation*: the link only redirects from a date and time; before, it redirects to an optional fallback URL, or is not found;
  - *expiry*: the link stops redirecting after a date;
  - *password*: visitors type a password before being redirected;
  - *landing page*: the link opens the landing page of its domain instead of redirecting directly;
  - free-form *metadata*.
- **Users and rights**: invite-only users, admins, and a role per domain (`viewer`, `editor`, `manager`). Domain managers invite people to their domains.
- **Sign-in your way**: Lika's own accounts, a Keycloak realm (any LDAP or identity provider behind it), or Firebase Authentication with Google sign-in.
- **API keys** for applications, scoped per domain or admin, and personal keys for AI assistants.
- **MCP server**: Claude, Cursor, VS Code or any MCP client creates and manages links with the rights of its user.
- **Import / export** of everything as JSON, whatever the database.
- **Databases**: SQLite, PostgreSQL, MongoDB, or in memory for development.
- **CDN-friendly**: serve short links through Cloudflare or CloudFront in front of the API.

## How it fits together

```
 visitors ──── https://go.example.com/slug ────┐
 apps, AI ──── REST API, /mcp (API keys) ──────┤
                                               ▼
                                     ┌───────────────────┐       ┌──────────────────────────┐
                                     │  API (lika-api)   │──────▶│ SQLite / PostgreSQL /    │
                                     │  Kotlin, Spring   │       │ MongoDB                  │
                                     └───────────────────┘       └──────────────────────────┘
                                         ▲         │ tokens, rights (`lika` claim)
                     /bff/api/** proxy   │         ▼
 people ── https://lika.example.com ─▶ ┌──────────────────┐   ┌───────────────────────────┐
                                       │ Web (lika-web)   │   │ Identity provider:        │
                                       │ Next.js          │   │ local, Keycloak, Firebase │
                                       └──────────────────┘   └───────────────────────────┘
```

| Component | Directory | What it does |
|---|---|---|
| API | [`api/`](api/README.md) | Redirects short links, serves the REST API (`/api/**`, OpenAPI at `/swagger-ui.html`) and the MCP server (`/mcp`). Kotlin, Spring Boot, JDK 21. |
| Web interface | [`web/`](web/README.md) | Manages links, users, API keys, domains and archives. Next.js; the browser never sees a token, the server keeps the session in an encrypted cookie. |

The API answers on every short link domain; the web interface has a domain of its own.

## Quick start

### Try it locally

Needs JDK 21 and Node.js 20.9+ (the image uses Node.js 24).

```bash
# Terminal 1: the API, in-memory database, dev profile (sample domains localhost:8080 and 127.0.0.1:8080)
cd api
LIKA_SETUP_TOKEN=my-setup-token ./gradlew :api:bootRun

# Terminal 2: the web interface
cd web
npm install
LIKA_API_URL=http://localhost:8080 LIKA_SESSION_SECRET=$(openssl rand -base64 48) npm run dev
```

Open http://localhost:3000, paste `my-setup-token` to create the first admin, then create a link on `localhost:8080` and open `http://localhost:8080/<slug>`.

### Run it on a server with Docker Compose

```bash
cp api/.env.example api/.env    # set LIKA_WEB_URL, LIKA_PUBLIC_API_URL, LIKA_JWT_SECRET, LIKA_SETUP_TOKEN
cp web/.env.example web/.env    # set LIKA_SESSION_SECRET
docker compose up -d --build
```

The web interface listens on port 3000, the API (short links) on 8080, with SQLite in the `lika-data` volume (`LIKA_DB=postgres` or `mongo` builds another database). Put a TLS reverse proxy in front of both.

### Deploy on Dokploy

Two applications built from this repository: see [docs/DOKPLOY.md](docs/DOKPLOY.md).

### After the first start

1. Copy the **setup token** from the API logs (or use the `LIKA_SETUP_TOKEN` you set), open the web interface, and create the first admin.
2. **Admin › Domains**: add your short link domain, e.g. `go.example.com` with the short URL base `https://go.example.com`, and point its DNS to the API.
3. **Users**: invite people with a role per domain. Without Mailjet, invitation links are shown to you to pass on.
4. **API Keys**: create keys for your applications. Each user can create a personal key for their AI assistant in **Profile**.

## Sign-in

`LIKA_AUTH_PROVIDER` chooses who holds the users' credentials. Whatever the provider, Lika keeps its users, invitations, rights and API keys, and nobody gets in without an invitation.

| Provider | Users sign in with | Install guide |
|---|---|---|
| `local` (default) | email and password stored by Lika | [docs/auth/LOCAL.md](docs/auth/LOCAL.md) |
| `keycloak` | the sign-in page of a Keycloak realm | [docs/auth/KEYCLOAK.md](docs/auth/KEYCLOAK.md) |
| `firebase` | email and password, and optionally Google, through Firebase Authentication | [docs/auth/FIREBASE.md](docs/auth/FIREBASE.md) |

## Rights

| Role | On its domain |
|---|---|
| `viewer` | reads and searches links |
| `editor` | also creates, edits and deletes links |
| `manager` | also invites users to the domain, sets their role there, and manages the API keys of the domain |
| admin | everything: domains, users, keys, archives |

Rights travel in the `lika` claim of the user's token, `{v, admin, domains}`, renamed with `LIKA_CLAIM_NAME` for Keycloak and Firebase. Lika's database stays the reference: the claim's `v` only tells outdated tokens apart. Details: [Keycloak](docs/auth/KEYCLOAK.md#claims), [Firebase](docs/auth/FIREBASE.md#claims). When an admin changes them, the version `v` changes and older tokens are refused until the web interface renews them, which it does by itself.

API keys are for applications: `viewer` or `editor` per domain, or admin. A user API key (`lika_u_…`) acts with the live rights of its user, on `/mcp` only.

## Using the API

```bash
# Domains the key may use, with their ids
curl https://go.example.com/api/domains -H "Authorization: Bearer $LIKA_API_KEY"

# Create a link (without a slug, one is generated)
curl -X POST https://go.example.com/api/links \
  -H "Authorization: Bearer $LIKA_API_KEY" -H "Content-Type: application/json" \
  -d '{"domainId": "<domain id>", "slug": "spring-sale", "targetUrl": "https://shop.example.com/spring"}'
```

The OpenAPI specification is served at `/v3/api-docs`, with Swagger UI at `/swagger-ui.html`. AI assistants connect to `https://go.example.com/mcp`: see [docs/MCP.md](docs/MCP.md).

## Documentation

| Document | Contents |
|---|---|
| [docs/CONFIGURATION.md](docs/CONFIGURATION.md) | Every environment variable of the API and the web interface, databases, email, CDN |
| [docs/auth/LOCAL.md](docs/auth/LOCAL.md) | Install guide: Lika's own accounts |
| [docs/auth/KEYCLOAK.md](docs/auth/KEYCLOAK.md) | Install guide: Keycloak |
| [docs/auth/FIREBASE.md](docs/auth/FIREBASE.md) | Install guide: Firebase Authentication and Google sign-in |
| [docs/DOKPLOY.md](docs/DOKPLOY.md) | Deploying on Dokploy, behind a CDN |
| [docs/MCP.md](docs/MCP.md) | Connecting AI assistants |
| [UPGRADING.md](UPGRADING.md) | What changes between versions |
| [api/README.md](api/README.md), [web/README.md](web/README.md) | Building and testing each component |
| [dev/README.md](dev/README.md) | Keycloak and the Firebase emulator for development |

## Development

```bash
# API
cd api
./gradlew build                      # in-memory database; -PdbSqlite, -PdbPostgres, -PdbMongo for the others
./gradlew test                       # Keycloak tests need Docker, Firebase tests a running Auth emulator
./gradlew :api:bootRun               # dev profile; -PdbSqlite / -PdbMongo to run on another database

# Web
cd web
npm run lint && npm run typecheck
npm test                             # Vitest
npm run test:e2e                     # Playwright, against a running API and web server: see web/README.md
npm run build                        # standalone server in .next/standalone
```

The API needs JDK 21: Kotlin 1.9 fails on newer JDKs.

## License

[GNU Affero General Public License v3.0](LICENSE).
