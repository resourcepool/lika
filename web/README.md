# Lika web

Admin interface of Lika: manage short links, users, API keys, domains and archives.

The browser talks to this server only. The server keeps the tokens of the signed-in user in an encrypted, httpOnly cookie and calls the [Lika API](../api) on the browser's behalf, through `/bff/api/**`. It renews the access token when it expires, or when the API refuses it because the user's rights changed (`token-stale`).

## Configuration

`LIKA_API_URL` and `LIKA_SESSION_SECRET` are required; the server refuses to start without them. See [docs/CONFIGURATION.md](../docs/CONFIGURATION.md#web-interface).

Sessions last as long as the browser keeps visiting at least once every 400 days and the API renews them, until sign-out, a password change, or the user being disabled. Single sign-on (Keycloak, Google) goes through `/auth/sso` and `/auth/callback`.

## Development

```bash
# In ../api: start the API with the in-memory database, and a known setup token
LIKA_SETUP_TOKEN=e2e-setup-token ./gradlew :api:bootRun

# Here
npm install
LIKA_API_URL=http://localhost:8080 LIKA_SESSION_SECRET=dev-secret-dev-secret-dev-secret-0123 npm run dev
```

Open http://localhost:3000: the setup page creates the first admin with the setup token (also printed in the API logs). Invite other users from **Users**. Invitation links are shown in the UI; set the Mailjet variables of the API to email them.

## Tests

```bash
npm run lint && npm run typecheck
npm test              # unit tests (Vitest)

# End-to-end (Playwright), against the API above with its default configuration
# (dev profile) and LIKA_SETUP_TOKEN=e2e-setup-token: the tests create the first admin if needed
npm run build && cp -r .next/static .next/standalone/.next/
LIKA_API_URL=http://localhost:8080 LIKA_SESSION_SECRET=... PORT=3000 node .next/standalone/server.js &
npx playwright install chromium   # or E2E_BROWSER_CHANNEL=chrome to use the installed Chrome
npm run test:e2e
```

The `E2E_*` variables point the tests elsewhere, e.g. at an API using Keycloak (`E2E_PROVIDER=keycloak`): see [docs/CONFIGURATION.md](../docs/CONFIGURATION.md#end-to-end-tests) and [dev/README.md](../dev/README.md).

## Docker

```bash
docker build -t lika-web .
docker run -p 8081:80 -e LIKA_API_URL=http://lika-api:8080 -e LIKA_SESSION_SECRET=$(openssl rand -base64 48) lika-web
```

`GET /health` answers 200 once the server is up.
