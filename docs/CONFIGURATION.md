# Configuration

Every setting of Lika, as environment variables. The API (`api` image) and the web interface (`www` image) read them when they start.

## API

### General

| Variable | Default | Description |
|---|---|---|
| `LIKA_WEB_URL` | `http://localhost:3000` | Public URL of the web interface, used in invitation and password reset links. **Set it in production.** |
| `LIKA_PUBLIC_API_URL` | — | Public URL of the API, e.g. `https://go.example.com`. Gives the MCP URL shown to users (`<url>/mcp`) and the base URL in the skills. |
| `LIKA_SETUP_TOKEN` | random | Setup token creating the first admin. Without it, a random one is printed in the logs while no admin exists. |
| `JAVA_OPTS` | — | Options of the JVM, e.g. `-Xmx512m` |
| `SERVER_PORT` | `8080` | |

### Sign-in

`LIKA_AUTH_PROVIDER` chooses who holds the users' credentials. The API refuses to start when the chosen provider is incompletely configured.

| `LIKA_AUTH_PROVIDER` | Users sign in with | Variables |
|---|---|---|
| `local` (default) | email and password stored by Lika | `LIKA_JWT_SECRET` |
| `keycloak` | the sign-in page of a Keycloak realm | `LIKA_KEYCLOAK_*` |
| `firebase` | email and password, and Google, through Firebase Authentication | `LIKA_FIREBASE_*` |

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
| `LIKA_FIREBASE_SERVICE_ACCOUNT` | Service account key: path to the JSON file, or the JSON itself. Its account needs the *Firebase Authentication Admin* role. |
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

The image tag chooses the database: `-sqlite` or `-mongo`.

| Variable | Description |
|---|---|
| `DB_SQLITE_PATH` | SQLite file, default `./data/db.sqlite`. Mount it on a volume. |
| `DB_MONGODB_CONNSTRING` | MongoDB connection string |
| `DB_MONGODB_APPDATABASE` | MongoDB database |

### Defaults to override in production

Without an active Spring profile, the API loads `application-default.yml`, made for development. It declares:

- the domains `localhost:8080` and `127.0.0.1:8080`, and sample links;
- the deprecated root key `lika-client-secret`, which becomes an **admin API key with a public value**.

In production, start with `SPRING_PROFILES_ACTIVE=prod` and your own configuration, or at least set `SECURITY_DEFAULTGLOBALACCESSTOKEN=` and `SECURITY_DEFAULTGLOBALPUBLICREADACCESSTOKEN=` (empty) so that no such key is created. Keys created by earlier versions must be deleted under **API Keys**.

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
| `E2E_ADMIN_TOKEN` | `lika-client-secret` | Admin API key, to set up data |
| `E2E_SETUP_TOKEN` | `e2e-setup-token` | `LIKA_SETUP_TOKEN` of the API |
| `E2E_ADMIN_EMAIL`, `E2E_ADMIN_PASSWORD` | `admin@e2e.test`, `e2e password 123` | First admin, created if needed |
| `E2E_KEYCLOAK_URL` | `http://localhost:8180` | Keycloak of the single sign-on specs |
| `E2E_BROWSER_CHANNEL` | — | `chrome` to use the installed Chrome |
