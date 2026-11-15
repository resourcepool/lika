# Deploying on Dokploy

[Dokploy](https://dokploy.com) builds Lika from this repository with `docker-compose.yml`: the API (short links, REST API, MCP server) and the web interface. It needs no registry and no CI.

## 1. Domains

Lika needs two kinds of public hosts, pointing to the Dokploy server:

| Host | Service | Serves |
|---|---|---|
| e.g. `lika.example.com` | `web`, port 80 | the web interface |
| e.g. `go.example.com`, one per short link domain | `api`, port 8080 | short links, `/api`, `/mcp` |

## 2. Create the service

1. In a project, **Create Service › Compose**.
2. **Provider**: this Git repository, branch `main`, compose path `./docker-compose.yml`. Enable **Autodeploy** to deploy on every push.
3. **Advanced › Isolated Deployments**: on. The `api` and `web` services then reach each other on their own network, and Dokploy routes to both.

## 3. Environment

In **Environment**, paste [`.env.example`](../.env.example) and fill it in. At least:

```bash
LIKA_WEB_URL=https://lika.example.com
LIKA_PUBLIC_API_URL=https://go.example.com
LIKA_SESSION_SECRET=   # openssl rand -base64 48
LIKA_JWT_SECRET=       # openssl rand -base64 48
```

The deployment fails as long as `LIKA_WEB_URL` or `LIKA_SESSION_SECRET` is missing. Every variable is described in [CONFIGURATION.md](CONFIGURATION.md): identity provider (Keycloak, Firebase), Mailjet, MCP. Dokploy writes them to a `.env` file, which `docker-compose.yml` passes to the containers.

`LIKA_DB=mongo`, with `DB_MONGODB_CONNSTRING`, builds the API for MongoDB instead of SQLite.

## 4. Domains

In **Domains**, add:

- `lika.example.com` → service `web`, port `80`, HTTPS (Let's Encrypt);
- `go.example.com` → service `api`, port `8080`, HTTPS. Add one entry per short link domain.

Dokploy adds the Traefik labels when deploying (**Preview Compose** shows them). Traefik forwards `X-Forwarded-Proto` and `X-Forwarded-Host`: session cookies are marked `Secure`, and single sign-on redirects to the right host.

## 5. Deploy and set up

1. **Deploy**. The web interface starts once the API is healthy (about a minute on the first start).
2. In the **Logs** of `api`, copy the setup token, or set `LIKA_SETUP_TOKEN` beforehand.
3. Open `https://lika.example.com`: the setup page creates the first admin.
4. In **Domains**, add `go.example.com` with the short URL base `https://go.example.com`. Then invite your users and give them roles.

With Keycloak, add `https://lika.example.com/auth/callback` to the redirect URIs of the client. With Google sign-in through Firebase, add it to the OAuth client.

## Data and backups

SQLite lives in the named volume `lika-data`. Back it up with **Volume Backups**, which only work with named volumes. **Import / Export** in the web interface also exports everything as JSON.

## Updates

Each deployment rebuilds both images from the repository. The API migrates its data on start; read [UPGRADING.md](../UPGRADING.md) before moving to a new version.

## Without Compose

`api/Dockerfile` and `web/Dockerfile` also build on their own, e.g. as two Dokploy **Applications** of type Dockerfile, with build paths `api` and `web`. The web interface then needs `LIKA_API_URL` set to the internal address of the API application. Compose is simpler: it wires both services for you.
