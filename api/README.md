# Lika - Permalink API

By default, you can run the in-memory DB for dev purposes

To build with MongoDB, you need to have a MongoDB instance running and then build the project with the `dbMongo` profile:
```bash
`./gradlew build -PdbMongo`

To build with SQLite, you need to have a file with the `dbPostgres` profile:
```bash
`./gradlew build -PdbSqlite`
