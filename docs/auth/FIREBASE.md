# Install guide: Firebase Authentication

With `LIKA_AUTH_PROVIDER=firebase`, accounts live in [Firebase Authentication](https://firebase.google.com/docs/auth). People sign in with their email and password, typed in Lika's own sign-in form, and optionally with **Google**.

Lika still keeps its users, invitations and rights. Nobody gets in without an invitation, even with an account in the Firebase project.

## How it works

- **Email and password**: the API checks them with Firebase (`accounts:signInWithPassword`) and gets a Firebase ID token and refresh token, which the web server keeps in its encrypted session cookie.
- **Google**: "Sign in with Google" sends the browser to Google, back to `<web URL>/auth/callback`. The API exchanges the code with Google, then signs in to Firebase with the Google ID token.
- **Accounts**: Lika links a Firebase account to a Lika user by its id, or the first time, by its **verified email**. When an invitation is accepted and no account has that email, Lika creates one with the password typed, marked verified.
- **Rights**: written as the custom claim `lika` of the account (or `LIKA_CLAIM_NAME`), next to the custom claims of other applications: see [Claims](#claims).
- **Through a service account**, Lika finds and creates accounts, sets passwords and writes claims. It never disables nor deletes Firebase accounts: disabling or deleting a user in Lika only removes their access to Lika (and their `lika` claim).
- **Tokens** are checked against Google's public keys, for the issuer `https://securetoken.google.com/<project id>` and the audience `<project id>`.

## Claims

### Standard claims Lika reads

Lika checks the signature of Firebase ID tokens with Google's public keys, then reads:

| Claim | Used for |
|---|---|
| `iss` | must be `https://securetoken.google.com/<LIKA_FIREBASE_PROJECT_ID>` |
| `aud` | must be `LIKA_FIREBASE_PROJECT_ID` |
| `exp`, `iat` | validity |
| `sub` | Firebase user id (`localId`), stored as the user's `externalId` |
| `email`, `email_verified` | linking an account to a Lika user the first time, only when the email is verified |
| `auth_time` | when the user signed in: *sign out everywhere*, a password reset or disabling the user refuse sessions opened before |

### The rights claim: `custom claim `lika``

Lika writes the rights of each user to the custom claims of their Firebase account, which Firebase puts in every ID token issued afterwards:

```json
"lika": { "v": 7, "admin": false, "domains": { "3f2a…": "editor", "9c1d…": "viewer" } }
```

| Field | Meaning |
|---|---|
| `v` | Version of the user's rights in Lika. It changes whenever their rights or status change, or their sessions are revoked. |
| `admin` | `true` for admins, who have every right; their `domains` is then empty. |
| `domains` | Role per domain id: `viewer`, `editor` or `manager`. |

How Lika uses it:

- **Lika's database stays the reference.** On every request, the rights applied are the ones Lika holds, not the ones in the token. The claim is there so that the token says who may do what, for Lika and for whoever reads it.
- **`v` detects outdated tokens.** A token whose `v` differs from the user's current version is refused with `401 token-stale`; the web interface renews it at once and gets a token with the new rights. A token without the claim (an account Lika has not written yet) is accepted, with the rights from Lika's database.
- **When it is written**: when Lika creates or links the account, whenever an admin or manager changes the user's rights or status, and at sign-in or renewal if the token still carries an older `v`. Deleting the user in Lika removes it from the account; the account itself stays.

### Custom claims of other applications

Firebase replaces all the custom claims of an account at each update. Lika reads them first and only changes its own: claims written by other applications sharing the project are kept, and removed by nobody but them.

Firebase limits all the custom claims of an account together to **1000 characters**. Lika's claim takes about 50 characters per domain: with other applications' claims, fewer domains fit. Granting more fails with *Too many domains for Firebase*; admins carry no domain list.

### Choosing the claim name: `LIKA_CLAIM_NAME`

The claim is named `lika` by default. If another application already uses a claim `lika`, give Lika another name:

```bash
LIKA_CLAIM_NAME=acme_lika
```

It replaces `lika` entirely (the custom claim takes that name). Letters, digits, `_` and `-` only: no dots, which Keycloak reads as nesting. The API refuses to start otherwise.

Changing it on a running install: restart the API. Existing tokens no longer carry the claim: they are accepted, and the claim is written under its new name at the users' next sign-in or token renewal. The old `lika` custom claim stays on the accounts until something removes it.

## Requirements

- A Firebase project (the free Spark plan is enough), or a Google Cloud project with Firebase added.
- Permission to create service account keys in that project. Some organizations forbid it (policy `iam.disableServiceAccountKeyCreation`).
- The public URL of the web interface, e.g. `https://lika.example.com`.

## 1. Project and Email/Password

1. In the [Firebase console](https://console.firebase.google.com), **Create a project**, or open an existing one.
2. **Build › Authentication › Get started**.
3. **Sign-in method › Add new provider › Email/Password**: enable *Email/Password* (not the passwordless email link). Save.
4. **Settings › User actions**: leave *Enable create (sign-up)* as you like; accounts signing up elsewhere get nothing in Lika without an invitation.

## 2. Project id and Web API key

**Project settings (gear) › General**:

- **Project ID**, e.g. `lika-prod-1a2b3` → `LIKA_FIREBASE_PROJECT_ID`.
- **Web API key** → `LIKA_FIREBASE_API_KEY`. If the project has none yet, **Add app › Web** creates one (no hosting needed).

If you restrict this key in Google Cloud (*APIs & Services › Credentials*), allow at least the **Identity Toolkit API** and the **Token Service API**. Do not restrict it by HTTP referrer: the API calls Firebase from the server.

## 3. Service account key

**Project settings › Service accounts › Firebase Admin SDK › Generate new private key** downloads a JSON key file. Its account, `firebase-adminsdk-…@<project>.iam.gserviceaccount.com`, has the rights Lika needs.

To use a dedicated account instead: in Google Cloud, **IAM & Admin › Service accounts › Create**, grant it the role **Firebase Authentication Admin**, then **Keys › Add key › JSON**.

Encode the file in base64 for `LIKA_FIREBASE_SERVICE_ACCOUNT`:

```bash
base64 -w0 key.json     # Linux
base64 -i key.json      # macOS
```

Line breaks in the value are ignored. The JSON itself, or the path to the file in the container, are accepted too. Keep the key secret, and delete the downloaded file once stored.

## 4. Google sign-in (optional)

1. **Authentication › Sign-in method › Add new provider › Google**: enable it, choose the support email, save.
2. Reopen **Google › Web SDK configuration**: copy the **Web client ID** and **Web client secret** → `LIKA_FIREBASE_GOOGLE_CLIENT_ID` and `LIKA_FIREBASE_GOOGLE_CLIENT_SECRET`.
3. In the [Google Cloud console](https://console.cloud.google.com) of the same project, **APIs & Services › Credentials**, open the OAuth client named *Web client (auto created by Google Service)*, and add to **Authorized redirect URIs**: `https://lika.example.com/auth/callback`.
4. **APIs & Services › OAuth consent screen**: set the application name and support email shown to people. For accounts outside your Google Workspace, the audience must be *External*, published.
5. Firebase **Authentication › Settings › Authorized domains**: add `lika.example.com`.

Set both variables or neither: the API refuses to start with only one.

Google accounts are linked to their invitation by their email, which Google verifies. If someone already has an email/password account with the same address, Firebase may merge the two or ask them to sign in with the original method, depending on **Settings › User account linking**.

## 5. Configure Lika

API (`api/.env`, or the environment of the API application in Dokploy):

```bash
LIKA_AUTH_PROVIDER=firebase
LIKA_FIREBASE_PROJECT_ID=lika-prod-1a2b3
LIKA_FIREBASE_API_KEY=AIza...
LIKA_FIREBASE_SERVICE_ACCOUNT=<base64 of the key file>
# Optional, Google sign-in
LIKA_FIREBASE_GOOGLE_CLIENT_ID=1234567890-abc.apps.googleusercontent.com
LIKA_FIREBASE_GOOGLE_CLIENT_SECRET=GOCSPX-...
LIKA_CLAIM_NAME=lika                            # optional: name of the rights claim, see Claims

LIKA_WEB_URL=https://lika.example.com
LIKA_SETUP_TOKEN=<openssl rand -hex 24>         # optional
```

`LIKA_JWT_SECRET` is not used: tokens are Firebase's. **Never set `FIREBASE_AUTH_EMULATOR_HOST` in production**: with it, tokens are not checked.

Web interface:

```bash
LIKA_API_URL=http://lika-api:8080
LIKA_SESSION_SECRET=<openssl rand -base64 48>
```

With Google sign-in behind a TLS-terminating reverse proxy, the web server must receive `X-Forwarded-Proto` and `X-Forwarded-Host` (Traefik and Dokploy send them): the redirect URI is built from the public host and must match the one registered in step 4 exactly.

## 6. First admin

1. Open the web interface: it shows the setup page. Paste the setup token (from the API logs, or `LIKA_SETUP_TOKEN`), type your name and email.
2. If the Firebase project has an account with that email, it is linked: no password is asked; sign in with it. Otherwise type a password of at least 10 characters: Lika creates the account and signs you in.

## 7. Invite users

**Users › Invite** works as with local accounts. The invitation page:

- for someone who already has a Firebase account with that email: just accepts; they then sign in with it (password or Google);
- for anyone else: asks for a password, creates the account, and signs them in.

Someone invited who clicks **Sign in with Google** directly, without opening the invitation, is linked as well.

## Passwords and sessions

- People change their password in **Profile**, and reset it with **Forgot password** on the sign-in page.
- Reset emails: with Mailjet configured, Lika sends its own link. Without it, Firebase sends its own reset email, from the template of **Authentication › Templates › Password reset** (customize the sender and the action URL there if you like).
- An admin can send a password reset link from **Users** for any linked user.
- Firebase tokens last one hour; the web interface renews them. **Sign out everywhere**, a reset password, or a disabled user end the user's Lika sessions at once (Lika checks when they signed in). The Firebase account stays enabled for other applications.
- An account disabled in Firebase cannot sign in to Lika either.

## Limits

- **Custom claims**: 1000 characters for all of an account's custom claims, those of other applications included. Granting a user too many domains fails with *Too many domains for Firebase*: make them admin, or give fewer domains.
- **Password policy**: Lika asks for 10 characters at least; a stricter Firebase password policy (*Settings › Password policy*) also applies.

## Troubleshooting

| Symptom | Cause |
|---|---|
| API refuses to start: `LIKA_AUTH_PROVIDER=firebase needs …` | A variable of step 5 is missing. |
| `Set both LIKA_FIREBASE_GOOGLE_CLIENT_ID and LIKA_FIREBASE_GOOGLE_CLIENT_SECRET, or neither` | Only one of them is set. |
| `LIKA_CLAIM_NAME may only hold letters, digits, _ and -` | Remove dots and other characters from the claim name. |
| `LIKA_FIREBASE_SERVICE_ACCOUNT is not a valid key` or `is neither JSON nor base64` | The value is not the base64 of the JSON key file, or was truncated. |
| `Firebase: CONFIGURATION_NOT_FOUND` | Authentication is not started in the Firebase console (step 1). |
| `Firebase: OPERATION_NOT_ALLOWED` | The Email/Password provider is not enabled. |
| `Firebase: API key not valid` | Wrong `LIKA_FIREBASE_API_KEY`, or the key is restricted without the Identity Toolkit API. |
| `403 PERMISSION_DENIED` from Identity Toolkit | The service account lacks *Firebase Authentication Admin*, or belongs to another project. |
| Google shows *redirect_uri_mismatch* | `<web URL>/auth/callback` is missing from the OAuth client's redirect URIs, or the reverse proxy does not forward the public host. |
| *This account has no access to Lika: ask an admin for an invitation* | No Lika user has this email, or the Firebase account's email is not verified (e.g. created by hand in the console). |

## Development

The Firebase Auth emulator needs no Google account: see [dev/README.md](../../dev/README.md).

All variables: [CONFIGURATION.md](../CONFIGURATION.md#firebase).
