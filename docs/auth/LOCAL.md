# Install guide: local accounts

With `LIKA_AUTH_PROVIDER=local`, the default, Lika is its own identity provider: it stores the passwords (hashed) in its database and signs the access tokens. Nothing else to run.

Choose it when nobody needs single sign-on. To move to Keycloak or Firebase later, see [Switching provider](#switching-provider).

## 1. Configure the API

In `api/.env` (Docker Compose) or the environment of the API application (Dokploy):

```bash
LIKA_AUTH_PROVIDER=local
LIKA_WEB_URL=https://lika.example.com          # links of invitations and password resets
LIKA_JWT_SECRET=<openssl rand -base64 48>       # 32+ characters
LIKA_SETUP_TOKEN=<openssl rand -hex 24>         # optional: otherwise a random one is printed in the logs
```

`LIKA_JWT_SECRET` signs the access tokens. Without it, a new secret is drawn at each start: the web interface renews sessions by itself, but **every API instance needs the same secret**, so set it as soon as you run more than one.

Access tokens last 15 minutes, refresh tokens 30 days; the web interface renews them while the browser keeps visiting.

### Email (recommended)

Without email, invitation and password reset links are shown to the admin, who passes them on. To email them through [Mailjet](https://www.mailjet.com):

```bash
MAILJET_API_KEY=...
MAILJET_API_SECRET=...
MAIL_FROM=lika@example.com     # a sender verified in Mailjet
MAIL_FROM_NAME=Lika
```

Without Mailjet, a user who forgot their password cannot reset it alone: an admin sends them a reset link from **Users**.

## 2. Configure the web interface

```bash
LIKA_API_URL=http://lika-api:8080              # the API, as the web container reaches it
LIKA_SESSION_SECRET=<openssl rand -base64 48>   # encrypts the session cookie
```

## 3. Create the first admin

1. Start the API. While no admin exists, it prints the setup token in its logs (or uses `LIKA_SETUP_TOKEN`).
2. Open the web interface: it shows the setup page. Paste the token, type your name, email and a password of at least 10 characters.
3. You are signed in as admin. The setup token stops working.

## 4. Invite users

**Users › Invite**: email, name, admin or a role per domain. The invitee opens the link, chooses a password, and is signed in. Invitations are single-use.

Users change their password in **Profile**, and can sign out of every browser there. Admins and domain managers disable users, which signs them out at once.

Sign-in is throttled per email after repeated failures.

## Checks

- The API logs `LIKA_JWT_SECRET is not set: using a random secret` → set `LIKA_JWT_SECRET`.
- `LIKA_JWT_SECRET must be at least 32 characters long` at start → use a longer secret.
- Invitation links point to `http://localhost:3000` → set `LIKA_WEB_URL`.

## Switching provider

Lika keeps users, invitations and rights whatever the provider. To move local users to Keycloak or Firebase:

1. Configure the new provider ([Keycloak](KEYCLOAK.md), [Firebase](FIREBASE.md)) and restart the API.
2. Users who have an account at that provider with the same email, **verified**, sign in with it: Lika links it to their user the first time, keeping their rights. Keycloak accounts coming from LDAP or another identity provider, and Google accounts with Firebase, usually are.
3. Users without such an account: delete them in **Users** and invite them again. Accepting the invitation creates their account at the provider with the password they choose. Password reset links do not help here: they only work for users already linked to an account.

Passwords stored by Lika are not exported to the new provider.

All variables: [CONFIGURATION.md](../CONFIGURATION.md#local).
