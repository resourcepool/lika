# Identity providers for development

Lika signs users in with its own accounts by default (`LIKA_AUTH_PROVIDER=local`). To work on Keycloak or Firebase:

## Keycloak

```bash
docker compose -f dev/docker-compose.yml up keycloak
```

Realm `lika` (`keycloak/lika-realm.json`) at http://localhost:8180, admin console with `admin` / `admin`. It has:

- the confidential client `lika` (secret `lika-dev-secret`), whose service account may manage users and clients. On start, Lika declares the `lika` attribute in the user profile and adds the protocol mapper to the client by itself;
- the user `ada@example.com` / `ada password 123`, to try linking an existing account to an invitation.

```bash
cd api
LIKA_AUTH_PROVIDER=keycloak LIKA_KEYCLOAK_URL=http://localhost:8180 LIKA_KEYCLOAK_REALM=lika \
LIKA_KEYCLOAK_CLIENT_ID=lika LIKA_KEYCLOAK_CLIENT_SECRET=lika-dev-secret ./gradlew :api:bootRun
```

The client also allows the password grant (`directAccessGrantsEnabled`), only so that tests can get tokens without a browser. Lika does not use it: disable it in production.

`KeycloakProviderTest` starts its own Keycloak with Testcontainers, when Docker is available.

## Firebase

The Auth emulator needs no Google account (`demo-` project):

```bash
cd dev/firebase && firebase emulators:start --only auth   # needs the Firebase CLI and Java
```

```bash
cd api
LIKA_AUTH_PROVIDER=firebase LIKA_FIREBASE_PROJECT_ID=demo-lika LIKA_FIREBASE_API_KEY=fake-api-key \
FIREBASE_AUTH_EMULATOR_HOST=localhost:9099 ./gradlew :api:bootRun
```

The emulator signs no token: never set `FIREBASE_AUTH_EMULATOR_HOST` in production. `FirebaseProviderTest` runs when the emulator is up.
