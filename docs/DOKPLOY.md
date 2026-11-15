# Deploying on Dokploy

[Dokploy](https://dokploy.com) builds Lika from this repository as two **Applications**:

| Application | Builds | Serves | Domain, e.g. |
|---|---|---|---|
| `lika-api` | `api/Dockerfile` | short links, REST API, MCP server (port 8080) | `go.example.com`, one per short link domain |
| `lika-web` | `web/Dockerfile` | the web interface (port 80) | `lika.example.com` |

The database is SQLite (a file in a volume, nothing else to run), PostgreSQL or MongoDB.

## 1. Database

- **SQLite**: nothing to create; a volume is mounted at step 2.
- **PostgreSQL**: in the project, **Create Service › Database › PostgreSQL**, deploy it, and copy its **Internal Connection URL** (`postgresql://user:password@host:5432/db`).
- **MongoDB**: same with **MongoDB**, copying its connection URL.

## 2. API

1. **Create Service › Application**, named e.g. `lika-api`.
2. **Provider**: this Git repository, branch `main`.
3. **Build Type**: Dockerfile, with **Dockerfile Path** `api/Dockerfile` and **Docker Context Path** `api`.
4. **Environment**: paste [`api/.env.example`](../api/.env.example) and fill it in. At least `LIKA_WEB_URL`, plus `LIKA_PUBLIC_API_URL` and `LIKA_JWT_SECRET`.
   - PostgreSQL: set `DB_POSTGRES_URL` to the internal connection URL, and the **Build Time Argument** `DB=postgres`.
   - MongoDB: `DB_MONGODB_CONNSTRING` and `DB_MONGODB_APPDATABASE`, and the build argument `DB=mongo`.
   - SQLite: nothing more; `DB=sqlite` is the default.
5. **Advanced › Mounts**, SQLite only: a **Volume Mount** named e.g. `lika-data`, mount path `/data`. Back it up with **Volume Backups**.
6. **Domains**: `go.example.com`, container port `8080`, HTTPS. Add one entry per short link domain.
7. **Deploy**. Note the **App Name** in **General**, e.g. `lika-api-x1y2z3`: it is the API's host name on Dokploy's network.

## 3. Web interface

1. **Create Service › Application**, named e.g. `lika-web`, same repository and branch.
2. **Build Type**: Dockerfile, with **Dockerfile Path** `web/Dockerfile` and **Docker Context Path** `web`.
3. **Environment**: paste [`web/.env.example`](../web/.env.example):
   - `LIKA_API_URL=http://<App Name of the API>:8080`, e.g. `http://lika-api-x1y2z3:8080`. If the web interface cannot reach it, use the public URL of the API instead, e.g. `https://go.example.com`.
   - `LIKA_SESSION_SECRET`: `openssl rand -base64 48`.
4. **Domains**: `lika.example.com`, container port `80`, HTTPS.
5. **Deploy**.

Traefik forwards `X-Forwarded-Proto` and `X-Forwarded-Host`: session cookies are marked `Secure`, and single sign-on redirects to the right host.

## 4. First admin

1. In the **Logs** of the API, copy the setup token, unless you set `LIKA_SETUP_TOKEN`.
2. Open `https://lika.example.com`: the setup page creates the first admin.
3. In **Domains**, add `go.example.com` with the short URL base `https://go.example.com`, then invite your users.

With Keycloak, add `https://lika.example.com/auth/callback` to the redirect URIs of the client. With Google sign-in through Firebase, add it to the OAuth client.

## Updates

Enable **Autodeploy** on both applications to deploy on every push to `main`. The API migrates its data on start; read [UPGRADING.md](../UPGRADING.md) before moving to a new version.

**Import / Export** in the web interface exports everything as JSON, whatever the database: also a way to move from SQLite to PostgreSQL.

Every variable: [CONFIGURATION.md](CONFIGURATION.md).
