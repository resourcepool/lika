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

Use the **Authorize** button with an access token (e.g. `lika-client-secret` in dev) to call protected endpoints.
