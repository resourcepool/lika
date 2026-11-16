# Configuration

Every setting of Lika, as environment variables. The API (`api` image) and the web interface (`www` image) read them when they start.

`api/Dockerfile` and `web/Dockerfile` build them from source; [`api/.env.example`](../api/.env.example) and [`web/.env.example`](../web/.env.example) list their variables. See [DOKPLOY.md](DOKPLOY.md), or `docker-compose.yml` to run both on one machine.

## API

### General

| Variable | Default | Description |
|---|---|---|
| `LIKA_WEB_URL` | `http://localhost:3000` | Public URL of the web interface, used in invitation and password reset links. **Set it in production.** |
| `LIKA_PUBLIC_API_URL` | — | Public URL of the API, e.g. `https://go.example.com`. Gives the MCP URL shown to users (`<url>/mcp`) and the base URL in the skills. |
| `LIKA_SETUP_TOKEN` | random | Setup token creating the first admin. Without it, a random one is printed in the logs while no admin exists. |
| `LIKA_HOST_HEADER` | — | Header carrying the original host of requests forwarded by a CDN, e.g. `X-Lika-Host`. See [Behind a CDN](#behind-a-cdn). |
| `JAVA_OPTS` | — | Options of the JVM, e.g. `-Xmx512m` |
| `SERVER_PORT` | `8080` | |

### Behind a CDN

The host of a request tells on which domain its slug is looked up. When a CDN serves `https://domain1.com/slug` from an origin of the API like `https://origin.example.com/slug`, the API reads the original host from, in order:

1. the header named by `LIKA_HOST_HEADER`, e.g. `X-Lika-Host`, that the CDN sets to the host it received;
2. `X-Forwarded-Host`;
3. `Host`.

When a header lists several hosts, one per proxy, the first one is used. Register `domain1.com` as a domain in Lika, with the short URL base `https://domain1.com`.

Prefer a dedicated header. Reverse proxies such as Traefik (Dokploy) replace `X-Forwarded-Host` with the host they received, unless they trust the CDN's addresses: `forwardedHeaders.trustedIPs` of their entry points. Custom headers go through untouched.

- **Cloudflare**: *Rules › Transform Rules › Modify Request Header*, set `X-Lika-Host` dynamically to `http.host`.
- **CloudFront**: CloudFront sends the origin's domain as `Host` and no `X-Forwarded-Host`.
  - One distribution per short link domain: in its origin, add the **custom header** `X-Lika-Host` with that domain, e.g. `domain1.com`.
  - One distribution for several domains (alternate domain names): a **CloudFront Function** on *viewer request* copies the host the visitor used, and the cache policy must be **CachingDisabled**. The cache key does not include the visitor's host, so `domain1.com/slug` and `domain2.com/slug` would share their cached redirect.

    ```js
    function handler(event) {
      var request = event.request;
      request.headers['x-lika-host'] = { value: request.headers.host.value };
      return request;
    }
    ```
  - Redirects are sent with `Cache-Control: no-store`: with a minimum TTL of 0, every visit reaches Lika, so expiry, activation and edits apply at once. With the managed policy *CachingOptimized*, redirects are cached for its minimum TTL of 1 second.
  - Allow the `POST` method in the cache behavior: the password form of protected links posts to the short URL.

To see why a link is not found, with the domain looked up and the headers it came from, set `LOGGING_LEVEL_LI_LIKA_NOTFOUND=DEBUG`:

```
Link not found: no link /epf on domain taki.li (id 3f2a…) [GET /epf; X-Lika-Host=taki.li, X-Forwarded-Host=lika.li, Host=lika.li; domain looked up: taki.li]
```

Whoever reaches the origin directly can pick the domain with this header. They only get the links of that domain, which are public anyway; password-protected links still need their password.

### Sign-in

`LIKA_AUTH_PROVIDER` chooses who holds the users' credentials. The API refuses to start when the chosen provider is incompletely configured.

| `LIKA_AUTH_PROVIDER` | Users sign in with | Variables |
|---|---|---|
| `local` (default) | email and password stored by Lika | `LIKA_JWT_SECRET` |
| `keycloak` | the sign-in page of a Keycloak realm | `LIKA_KEYCLOAK_*` |
| `firebase` | email and password, and Google, through Firebase Authentication | `LIKA_FIREBASE_*` |

Step-by-step install guides: [local](auth/LOCAL.md), [Keycloak](auth/KEYCLOAK.md), [Firebase](auth/FIREBASE.md).

Whatever the provider, Lika keeps its users, invitations, rights and API keys. Rights are written to the provider as the `lika` claim of the users' tokens. Lika never disables or deletes accounts at the provider, because other applications may use them. A user who is disabled or removed in Lika only loses access to Lika.

#### Local

| Variable | Default | Description |
|---|---|---|
| `LIKA_JWT_SECRET` | random | At least 32 characters, signing the access tokens. Without it, a new one is drawn at each start: the web interface renews sessions by itself, but **every API instance needs the same secret**. |

#### Keycloak

| Variable | Description |
|---|---|
| `LIKA_KEYCLOAK_URL` | Base URL of Keycloak, e.g. `https://sso.example.com` |
| `LIKA_KEYCLOAK_REALM` | Realm of the users |
| `LIKA_KEYCLOAK_CLIENT_ID` | Confidential OpenID Connect client of Lika |
| `LIKA_KEYCLOAK_CLIENT_SECRET` | Its secret |
| `LIKA_KEYCLOAK_LABEL` | Label of the sign-in button: "Sign in with *label*". Default `Keycloak`. |

The client needs:

- **Standard flow** with PKCE (S256), and the redirect URI `<LIKA web URL>/auth/callback`.
- **Service account** with the `realm-management` roles `manage-users`, `view-users`, `query-users`, `manage-clients`, `view-clients`, `query-clients`, `manage-realm` and `view-realm`. On start, Lika declares the `lika` attribute in the realm's user profile (visible and editable by admins only) and adds the protocol mapper putting it in the tokens of its client. Both are idempotent.
- No direct access grant: Lika does not use it.

Users who already have an account in the realm, possibly through LDAP or another identity provider, are linked to their invitation by email the first time they sign in. Their email must be verified in Keycloak. Passwords are changed and reset in Keycloak. An admin can still send a password reset link from Lika: it sets the password of a Keycloak account.

`dev/keycloak/lika-realm.json` is a working example (see [dev/README.md](../dev/README.md)).

#### Firebase

| Variable | Description |
|---|---|
| `LIKA_FIREBASE_PROJECT_ID` | Firebase project id |
| `LIKA_FIREBASE_API_KEY` | Web API key of the project (*Project settings › General*) |
| `LIKA_FIREBASE_SERVICE_ACCOUNT` | Service account key file (*Project settings › Service accounts › Generate new private key*), **encoded in base64**: `base64 -w0 key.json`, or `base64 -i key.json` on macOS. Line breaks are ignored. The JSON itself, or the path to the file, are also accepted. Its account needs the *Firebase Authentication Admin* role. |
| `LIKA_FIREBASE_GOOGLE_CLIENT_ID`, `LIKA_FIREBASE_GOOGLE_CLIENT_SECRET` | Optional: enables "Sign in with Google". The OAuth web client of the Google provider of Firebase (*Authentication › Sign-in method › Google › Web SDK configuration*), with the redirect URI `<LIKA web URL>/auth/callback` added in Google Cloud. |
| `FIREBASE_AUTH_EMULATOR_HOST` | `host:port` of the Auth emulator, for development and tests only: its tokens are not signed. |

The *Email/Password* provider must be enabled in Firebase. Rights are written as the custom claim `lika`. Firebase limits custom claims to 1000 characters, about 15 to 20 domains per user; admins need none.

Google accounts are linked to their invitation by their verified email. Without Mailjet, Firebase emails password reset links itself.

### Email

Invitations and password reset links are emailed through [Mailjet](https://www.mailjet.com) when these are set. Otherwise, links are shown to the admin, who passes them on.

| Variable | Description |
|---|---|
| `MAILJET_API_KEY`, `MAILJET_API_SECRET` | API credentials |
| `MAIL_FROM` | Sender address, verified in Mailjet |
| `MAIL_FROM_NAME` | Sender name, default `Lika` |

### MCP server

| Variable | Description |
|---|---|
| `LIKA_MCP_ALLOWED_ORIGINS` | Comma-separated origins of browser-based MCP clients allowed on `/mcp`, or `*`. Requests from other origins are refused. Desktop and server clients send no origin and are always accepted. |

See [MCP.md](MCP.md).

### Database

The database is chosen when building: build argument `DB` of `api/Dockerfile` (`sqlite` by default, `postgres`, `mongo`, or `memory` which loses everything on restart), or Gradle flag `-PdbSqlite`, `-PdbPostgres`, `-PdbMongo`. The images of the CI are tagged `-sqlite`, `-mongo` or `-memory`. Tables and collections are created and updated on start.

| Variable | Description |
|---|---|
| `DB_SQLITE_PATH` | SQLite file: `/data/lika.db` in the image built by `api/Dockerfile` (mount a volume on `/data`), `./data/db.sqlite` otherwise |
| `DB_POSTGRES_URL` | PostgreSQL: `jdbc:postgresql://host:5432/lika`, or `postgresql://user:password@host:5432/lika` (as Dokploy shows it; URL-encode special characters of the password) |
| `DB_POSTGRES_USERNAME`, `DB_POSTGRES_PASSWORD` | Override the credentials of the URL |
| `DB_POSTGRES_MAXIMUMPOOLSIZE` | Connections of the pool, default 10 |
| `DB_MONGODB_CONNSTRING` | MongoDB connection string |
| `DB_MONGODB_APPDATABASE` | MongoDB database |

### Development profile

`SPRING_PROFILES_ACTIVE=dev` loads `application-dev.yml`. It declares the domains `localhost:8080` and `127.0.0.1:8080`, sample links, open CORS, and the deprecated root key `lika-client-secret` as an admin API key. `./gradlew :api:bootRun` and the tests use it. **Never in production**: the value of that key is public.

Images start without it. Lika < 0.7 applied these defaults whenever no profile was active, so an existing database may hold an account with the public token `lika-client-secret`. When upgrading, it is not migrated to an API key.

## Web interface

| Variable | Required | Description |
|---|---|---|
| `LIKA_API_URL` | yes | URL of the API as reachable from the web container, e.g. `http://lika-api:8080`. `API_BASE_URL` (0.5) is still read, with a warning. |
| `LIKA_SESSION_SECRET` | yes | At least 32 random characters encrypting the session cookie, e.g. `openssl rand -base64 48`. Changing it signs everyone out. |
| `PORT` | no | 80 in the image, 3000 otherwise |

The server refuses to start without the required ones. Behind a TLS-terminating reverse proxy, forward `X-Forwarded-Proto` and `X-Forwarded-Host`: the cookies are then marked `Secure`, and the single sign-on redirect URI uses the public host.

## End-to-end tests

| Variable | Default | Description |
|---|---|---|
| `E2E_BASE_URL` | `http://localhost:3000` | Web interface under test |
| `E2E_API_URL` | `http://localhost:8080` | Its API |
| `E2E_PROVIDER` | `local` | Provider of the API: `local` and `firebase` run the password specs, `keycloak` the single sign-on specs |
| `E2E_ADMIN_TOKEN` | `lika-client-secret` | Admin API key, to set up data: the root key of the `dev` profile |
| `E2E_SETUP_TOKEN` | `e2e-setup-token` | `LIKA_SETUP_TOKEN` of the API |
| `E2E_ADMIN_EMAIL`, `E2E_ADMIN_PASSWORD` | `admin@e2e.test`, `e2e password 123` | First admin, created if needed |
| `E2E_KEYCLOAK_URL` | `http://localhost:8180` | Keycloak of the single sign-on specs |
| `E2E_BROWSER_CHANNEL` | — | `chrome` to use the installed Chrome |
