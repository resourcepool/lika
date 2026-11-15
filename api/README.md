# Lika - Permalink API

By default, you can run the in-memory DB for dev purposes

To build with MongoDB, you need to have a MongoDB instance running and then build the project with the `dbMongo` profile:
```bash
`./gradlew build -PdbMongo`

To build with SQLite, you need to have a file with the `dbPostgres` profile:
```bash
`./gradlew build -PdbSqlite`

## API documentation

When the API is running, the OpenAPI spec and Swagger UI are available at:
- Swagger UI: http://localhost:8080/swagger-ui.html
- OpenAPI spec (JSON): http://localhost:8080/v3/api-docs (YAML: `/v3/api-docs.yaml`)

Use the **Authorize** button with a user access token (from `POST /api/auth/login`) or an API key (e.g. the deprecated root key `lika-client-secret` in dev) to call protected endpoints.

## Users and access

On first start, the logs print a setup token: the web interface uses it to create the first admin. Users sign in with Lika's own accounts, Keycloak or Firebase (`LIKA_AUTH_PROVIDER`).

- Every environment variable: [docs/CONFIGURATION.md](../docs/CONFIGURATION.md)
- Roles, API keys and upgrading from 0.6: [UPGRADING.md](../UPGRADING.md)
- Keycloak and the Firebase emulator for development: [dev/README.md](../dev/README.md)

Tests run against the in-memory database. `KeycloakProviderTest` needs Docker, `FirebaseProviderTest` a running Auth emulator; both are skipped otherwise.
