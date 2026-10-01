# Upgrading Lika

## 0.4.x → 0.5.0

The API migrates existing data on first start. Nothing to do by hand, but read the behavior changes below.

### Automatic migrations (SQLite, MongoDB)

- **Domain grants.** Before 0.5.0, every API key could manage the links of every standard domain (the ones listed under `lika.domains` in the configuration). Keys created by an older version are granted those domains once, so they keep the same access after the upgrade. Keys created afterwards start with no grant.
- **Link passwords** stored in clear text are replaced by their BCrypt hash.

### Access model

- A **global** key administers everything: all domains and `/api/admin/**`. Creating an account with `global: true` now works (the flag was ignored before).
- Any other key manages the links of the domains it **owns** or was **granted** (`PUT /api/admin/accounts/{id}/domains`). Its read-write token manages those links; its read token can only read them through `/api/search/**`.
- Standard domains are no longer open to every key: grant them explicitly.
- Every key granted on a domain manages **all** its links, whoever created them. `Deeplink.ownerId` is renamed `createdBy`.
- Links of other domains are reported as not found (404), including by id.

### API changes

| Endpoint | Change |
|---|---|
| `POST /api/admin/accounts` | honors `global`, accepts `domainIds` |
| `PATCH /api/admin/accounts/{id}` | new: rename (`{name}`) |
| `POST /api/admin/accounts/{id}/regenerate` | new: `{token: "read_write" \| "read"}`; the old token stops working immediately |
| `PUT /api/admin/accounts/{id}/domains` | new: replaces the domains granted to the key |
| `DELETE /api/admin/accounts/{id}` | 409 when deleting the last global account |
| `PATCH /api/admin/domains/{id}` | new: updates `apiBaseUrl` / `landingBaseUrl` (the host cannot change) and the short URLs of its links |
| `DELETE /api/admin/domains/{id}` | 409 if the domain still has links, unless `?force=true`, which deletes them too; grants on the domain are removed |
| `GET /api/links`, `/api/admin/links` | `domainId` is optional: without it, lists the links of every accessible domain |
| `GET /api/domains` | returns owned and granted domains (all domains for a global key) |
| `POST /api/admin/archive/export` | exports archive v2 (see below) |
| `POST /api/admin/archive/import` | reads v1 and v2 archives; `?dryRun=true` reports without writing; `?conflict=skip\|overwrite` (default `skip`); returns a report |

`/api/admin/links/**` is kept as an alias of `/api/links/**`, restricted to admins.

### Passwords

- Link responses never contain the password: `options.password` is `{"enabled": true}` on protected links.
- When editing a link, **omitting `options.password` keeps the current password**. Send `{"password": "..."}` to replace it or `{"remove": true}` to remove the protection.
- The password page of a protected link posts the password to the server, which redirects on success. It no longer embeds any token.

### Link behavior

- Before its activation date, a link redirects to `targetUrlBeforeActivation`, or is not found if there is none.
- A link is not found as soon as its expiry date has passed (it used to work until the next nightly cleanup). The cleanup now also runs on MongoDB, where it never matched any link.
- Editing a link keeps its creation date.

### Archive v2

Archives now hold `"version": 2`, a top-level `domains` list (with their `owner` and links), and per account the `grants` (domain hosts). Links keep their metadata, password hash, creator and timestamps. Version 1 archives still import: their non-global accounts are granted the standard domains, as in the migration above.

Exports contain every API key in clear text: store them accordingly.
