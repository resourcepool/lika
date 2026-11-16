# Install guide: Keycloak

With `LIKA_AUTH_PROVIDER=keycloak`, people sign in on the sign-in page of a [Keycloak](https://www.keycloak.org) realm, through the authorization code flow with PKCE. Whatever the realm offers comes with it: LDAP or Active Directory, other identity providers, two-factor authentication, password policies.

Lika still keeps its users, invitations and rights. Nobody gets in without an invitation, even with an account in the realm.

## How it works

- **Sign-in**: "Sign in with *label*" in the web interface opens Keycloak, which sends the browser back to `<web URL>/auth/callback`. The web server exchanges the code through the API, which holds the client secret.
- **Accounts**: Lika links a Keycloak account to a Lika user by its id, or the first time, by its **verified email**. When an invitation is accepted and no account has that email, Lika creates one in the realm with the password typed.
- **Rights**: Lika writes them to the user attribute `lika`; a protocol mapper of the client puts it in the tokens as the `lika` claim. Lika declares the attribute (visible and editable by realm admins only) and creates the mapper itself on start.
- **Through the service account** of the client, Lika finds, creates and updates accounts, sets passwords and lists sessions. It never disables nor deletes Keycloak accounts: disabling or deleting a user in Lika only removes their access to Lika.
- **Tokens** are only accepted when issued to Lika's client (`azp`) by the realm.

## Requirements

- Keycloak 24 or newer (declarative user profile). Tested with 26.4.
- Reachable by the API at `LIKA_KEYCLOAK_URL`, and by browsers at the same URL: the API discovers the endpoints from `<url>/realms/<realm>/.well-known/openid-configuration`, and sends browsers to the authorization endpoint it gives. Set Keycloak's `KC_HOSTNAME` to its public URL.
- The public URL of the web interface, e.g. `https://lika.example.com`.

## 1. Realm

Use an existing realm, or **Create realm**, e.g. `lika`. In **Realm settings › Login**:

- **Login with email**: on. Lika identifies people by email.
- **Duplicate emails**: off.
- **Forgot password**: on, so that people reset their password themselves on the Keycloak page.
- **User registration**: off. Accounts registered on the Keycloak page get nothing without an invitation.
- **Verify email**: as you like. Accounts created by Lika are marked verified.

Set up **Email** in Realm settings if Keycloak should send password reset emails.

## 2. Client

**Clients › Create client**:

1. **General settings**: *Client type* `OpenID Connect`, *Client ID* `lika`, *Name* `Lika`.
2. **Capability config**:
   - *Client authentication*: **on** (confidential client).
   - *Authentication flow*: **Standard flow** on; **Direct access grants** off (Lika does not use it); **Service accounts roles** on.
3. **Login settings**:
   - *Valid redirect URIs*: `https://lika.example.com/auth/callback`
   - *Web origins*: leave empty. The browser never calls Keycloak's API: the web server does.
4. **Save**, then in the client:
   - **Advanced › Advanced settings**: *Proof Key for Code Exchange Code Challenge Method* `S256`.
   - **Credentials**: copy the **Client Secret**.

## 3. Service account roles

In the client, **Service accounts roles › Assign role › Filter by clients**, select these roles of `realm-management`:

| Role | Used to |
|---|---|
| `view-users`, `query-users`, `manage-users` | find accounts by email, create them, write the `lika` attribute, set passwords, list sessions |
| `view-clients`, `query-clients`, `manage-clients` | find the client and add the `lika rights` protocol mapper |
| `view-realm`, `manage-realm` | declare the `lika` attribute in the realm's user profile |

The `manage-clients` and `manage-realm` roles are only needed for the setup Lika does on its first start. Once the logs show it done (step 5), you may remove them, as long as the attribute and the mapper are left in place.

## 4. Configure Lika

API (`api/.env`, or the environment of the API application in Dokploy):

```bash
LIKA_AUTH_PROVIDER=keycloak
LIKA_KEYCLOAK_URL=https://sso.example.com       # base URL, without /realms
LIKA_KEYCLOAK_REALM=lika
LIKA_KEYCLOAK_CLIENT_ID=lika
LIKA_KEYCLOAK_CLIENT_SECRET=<client secret>
LIKA_KEYCLOAK_LABEL=Example SSO                 # button "Sign in with Example SSO"; default Keycloak

LIKA_WEB_URL=https://lika.example.com
LIKA_SETUP_TOKEN=<openssl rand -hex 24>         # optional
```

`LIKA_JWT_SECRET` is not used: tokens are Keycloak's.

Web interface:

```bash
LIKA_API_URL=http://lika-api:8080
LIKA_SESSION_SECRET=<openssl rand -base64 48>
```

Behind a TLS-terminating reverse proxy, the web server must receive `X-Forwarded-Proto` and `X-Forwarded-Host` (Traefik and Dokploy send them): the redirect URI is built from the public host, and must match the one registered in step 2 exactly.

## 5. Start and check

Start the API. Its logs show, the first time:

```
Declared the attribute lika in the user profile of realm lika
Created the protocol mapper lika rights on client lika
```

If Keycloak is not reachable yet, the API starts anyway (`Keycloak is not ready yet (...); setup will be retried on first use`) and does the setup on the first sign-in. Check in Keycloak:

- **Realm settings › User profile**: an attribute `lika`, *Lika rights*.
- **Clients › lika › Client scopes › lika-dedicated**: a mapper `lika rights`, *User Attribute*, claim `lika`, JSON.

## 6. First admin

1. Open the web interface: it shows the setup page. Paste the setup token (from the API logs, or `LIKA_SETUP_TOKEN`) and type your name and email.
2. If the realm has an account with that email, it is linked: no password is asked, sign in with that account. Otherwise, type a password of at least 10 characters (and compliant with the realm's password policy): Lika creates the account in the realm.
3. Click **Sign in with *label***, and sign in on the Keycloak page.

## 7. Invite users

**Users › Invite** works as with local accounts. The invitation page then:

- for someone who already has an account in the realm, possibly through LDAP or another identity provider: just accepts, then sends them to Keycloak to sign in;
- for anyone else: asks for a password, creates their account in the realm, then sends them to Keycloak.

Someone invited who signs in with Keycloak directly, without opening the invitation, is linked as well, provided their email is verified in Keycloak.

## Passwords and sessions

- People change and reset their password in Keycloak: **Profile** in Lika links to the realm's account console. The *Forgot password* page of Lika is not used.
- An admin can still send a password reset link from **Users**: it sets the password of the Keycloak account of a linked user.
- **Sign out everywhere** in Lika, a reset password, or a disabled user end the user's Lika sessions: Lika records their Keycloak session ids and refuses them. The Keycloak session itself stays open for other applications.
- Session lengths follow the realm: **Realm settings › Sessions** and **Tokens**. The web interface renews tokens with the refresh token while the Keycloak session lasts.

## Troubleshooting

| Symptom | Cause |
|---|---|
| API refuses to start: `LIKA_AUTH_PROVIDER=keycloak needs LIKA_KEYCLOAK_…` | A variable of step 4 is missing. |
| `Keycloak client lika not found in realm lika` | Wrong `LIKA_KEYCLOAK_CLIENT_ID`, or the service account lacks `view-clients`/`query-clients`. |
| `403` from Keycloak in the API logs | A role of step 3 is missing. |
| Keycloak shows *Invalid parameter: redirect_uri* | The redirect URI is not `<web URL>/auth/callback` exactly: check *Valid redirect URIs*, and the forwarded headers of the reverse proxy. |
| *This account has no access to Lika: ask an admin for an invitation* | No Lika user has this email, or the email is not verified in Keycloak. |
| `unauthorized_client` on sign-in | *Client authentication* is off, or the secret is wrong. |
| Rights changes are not applied | The `lika rights` mapper was removed or edited: delete it and restart the API to recreate it. |

## Development

`dev/docker-compose.yml` runs Keycloak with the realm `dev/keycloak/lika-realm.json`, already configured: see [dev/README.md](../../dev/README.md). That realm enables direct access grants for tests only.

All variables: [CONFIGURATION.md](../CONFIGURATION.md#keycloak).
