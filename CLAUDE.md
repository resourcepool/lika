# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

Lika is an open-source permalink API consisting of two main components:
- **Backend API** (`api/`): Kotlin/Spring Boot application for permalink management
- **Frontend Web App** (`web/`): Next.js application for the user interface

## Development Commands

### Backend (Kotlin/Spring Boot)
```bash
# Navigate to API directory
cd api

# Build the project
./gradlew build

# Run the application (default uses in-memory H2 database)
# Needs JDK 21 (Kotlin 1.9 fails on 25). bootRun and the tests use the dev profile (application-dev.yml);
# images start without it. The first admin is created with the setup token printed in the logs.
./gradlew :api:bootRun

# Run with SQLite database
./gradlew :api:bootRun -PdbSqlite

# Run with MongoDB database  
./gradlew :api:bootRun -PdbMongo

# Keycloak / Firebase providers: see dev/README.md, install guides in docs/auth/. Every env variable: docs/CONFIGURATION.md

# Run tests
./gradlew test

# Clean build
./gradlew clean
```

### Frontend (Next.js)
```bash
cd web
npm install

# Development server (needs a running API, see web/README.md)
LIKA_API_URL=http://localhost:8080 LIKA_SESSION_SECRET=<32+ random chars> npm run dev

npm run lint
npm run typecheck
npm test            # Vitest unit tests
npm run test:e2e    # Playwright, against a running API (LIKA_SETUP_TOKEN=e2e-setup-token) and web server
npm run build       # Standalone server in .next/standalone
```

## Architecture

### Backend Architecture
- **Modular Structure**: Multi-module Gradle project with separation of concerns
  - `api`: Main Spring Boot application and REST controllers
  - `common`: Shared models, DTOs, and interfaces
  - `client-http`: HTTP client implementations for API consumption
  - `security`: Authentication and authorization components
  - `db-memory`, `db-sqlite`, `db-postgres`, `db-mongo`: Pluggable database implementations
- **Database Abstraction**: DAO pattern with multiple backend support (H2/SQLite/PostgreSQL/MongoDB); `db-memory`, `db-sqlite` and `db-postgres` share the same JPA entities
- **Core Models**: `Deeplink`, `Domain`, `User`, `ApiKey` with support for advanced features like activation scheduling and expiry dates
- **Users and access**: users sign in through the identity provider chosen by `LIKA_AUTH_PROVIDER` (`local`, `keycloak`, `firebase`: `IdentityProvider` implementations in `api/.../core/services/auth`, one bean per value). Lika keeps users, invitations and rights in all cases; external accounts are linked by `externalId` or verified email, and only the `lika` claim is written to the provider; rights are the `lika` claim `{v, admin, domains: {domainId: viewer|editor|manager}}` of their token. API keys are for applications (viewer/editor per domain, or admin). Every check goes through `AuthorizationService`; `CredentialsService` resolves a bearer (JWT → user, else key secret). A token whose `v` differs from the user's version is refused (`token-stale`).
- **MCP server**: `POST /mcp`, stateless Streamable HTTP implemented by hand (`core/services/mcp`: `McpServer` JSON-RPC, `McpTools` filtered per caller, `McpSkills` generated per caller as `skill://<name>/SKILL.md` resources, served with `skills/list`/`skills/get` of the Skills over MCP extension). User API keys (`ApiKey.userId`, `lika_u_…`) act with their user's live rights, `/mcp` only (`role_mcp`). Integration doc: `docs/MCP.md`.
- **Spring Security**: one filter authenticating user tokens and API keys (`security` module), coarse URL rules in `SecurityConfig`, per-domain rules in the services
- **Caching**: Caffeine cache integration for performance optimization

### Frontend Architecture
- **Next.js (App Router)**, React 19, TypeScript, Tailwind CSS v4, shadcn/ui (Radix), TanStack Query and Table, react-hook-form + zod
- **Backend for the browser**: the browser never sees tokens. Sign-in stores the access and refresh tokens in an encrypted httpOnly cookie (`src/lib/session.ts`); `/bff/api/**` (`src/app/bff`) forwards calls to the API and renews the token (`src/lib/auth.ts`). Auth flows are server actions (`src/app/actions.ts`). `src/proxy.ts` redirects visitors without a session and slides the cookie expiry.
- **Runtime configuration**: `LIKA_API_URL` and `LIKA_SESSION_SECRET` are read at startup (`src/lib/env.ts`, checked in `src/instrumentation.ts`)
- **Key Routes**:
  - `/links`: link management, filtered by domain
  - `/users`, `/keys`: admins and domain managers (`src/app/(app)/(manage)/layout.tsx`)
  - `/admin/domains`, `/admin/archive`: admin only (`src/app/(app)/admin/layout.tsx`)
  - `/profile`: the signed-in user
  - `/signin`, `/setup`, `/invite/[token]`, `/forgot-password`, `/reset-password/[token]`: public
- `web/AGENTS.md`: this Next.js version differs from older ones; read `web/node_modules/next/dist/docs/` before changing framework code

### Deployment
- `api/Dockerfile` (multi-stage, build arg `DB`: sqlite|postgres|mongo|memory) and `web/Dockerfile` build from source, each with its `.env.example`; `docs/DOKPLOY.md` deploys them as two Dokploy applications; `docker-compose.yml` runs both on one machine
- GitLab CI (`.gitlab-ci.yml`): tests, then on release tags `vX.Y.Z` the `package` stage builds `api/Dockerfile` (one image per `DB`) and `web/Dockerfile` for amd64 (GitLab runners) and arm64 (group runner tagged `arm64`, privileged for docker:dind), pushed as `<tag>-<arch>`; the `publish` stage joins them into multi-arch tags in the project registry (`$CI_REGISTRY_IMAGE/api`, `/www`); by hand on `main`
- `api/api/Dockerfile` packages a prebuilt jar, for the GitHub workflow only

### Database Selection
The API supports three database backends controlled by Gradle properties:
- Default: In-memory H2 (for development/testing)
- Production options: SQLite (`-PdbSqlite`), PostgreSQL (`-PdbPostgres`) or MongoDB (`-PdbMongo`)

### Key Business Logic
- **Permalink Types**: Simple links with optional advanced features (activation scheduling, landing pages, expiry)
- **Multi-tenancy**: Domain-based separation with host-specific permalink management
- **Security Model**: invite-only users with a role per domain, admins managing everything; the first admin is created with a setup token