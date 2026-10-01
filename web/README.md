# Lika web

Admin interface of Lika: manage short links, domains, API keys and archives.

The browser talks to this server only. The server keeps the signed-in API key in an encrypted, httpOnly cookie and calls the [Lika API](../api) on the browser's behalf, through `/bff/api/**`.

## Configuration

Read when the server starts:

| Variable | Required | Description |
|---|---|---|
| `LIKA_API_URL` | yes | URL of the Lika API, as reachable from this server, e.g. `http://lika-api:8080` |
| `LIKA_SESSION_SECRET` | yes | Random string of at least 32 characters encrypting the session cookie, e.g. `openssl rand -base64 48`. Changing it signs everyone out. |
| `PORT` | no | Listening port: 80 in the Docker image, 3000 otherwise |

The server refuses to start without them. Sessions last as long as the browser keeps visiting at least once every 400 days, until sign-out or until the key is regenerated or deleted.

Behind a TLS-terminating reverse proxy, forward `X-Forwarded-Proto` so the cookie is marked `Secure`.

## Development

```bash
# In ../api: start the API with the in-memory database
./gradlew :api:bootRun

# Here
npm install
LIKA_API_URL=http://localhost:8080 LIKA_SESSION_SECRET=dev-secret-dev-secret-dev-secret-0123 npm run dev
```

Sign in at http://localhost:3000 with the root key of the API configuration (`lika-client-secret` by default).

## Tests

```bash
npm run lint && npm run typecheck
npm test              # unit tests (Vitest)

# End-to-end (Playwright), against the API above with its default configuration
npm run build && cp -r .next/static .next/standalone/.next/
LIKA_API_URL=http://localhost:8080 LIKA_SESSION_SECRET=... PORT=3000 node .next/standalone/server.js &
npx playwright install chromium   # or E2E_BROWSER_CHANNEL=chrome to use the installed Chrome
npm run test:e2e
```

`E2E_BASE_URL`, `E2E_API_URL` and `E2E_ADMIN_TOKEN` point the tests elsewhere.

## Docker

```bash
docker build -t lika-web .
docker run -p 8081:80 -e LIKA_API_URL=http://lika-api:8080 -e LIKA_SESSION_SECRET=$(openssl rand -base64 48) lika-web
```

`GET /health` answers 200 once the server is up.
