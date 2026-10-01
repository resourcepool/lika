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
./gradlew :api:bootRun

# Run with SQLite database
./gradlew :api:bootRun -PdbSqlite

# Run with MongoDB database  
./gradlew :api:bootRun -PdbMongo

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
npm run test:e2e    # Playwright, against a running API and web server
npm run build       # Standalone server in .next/standalone
```

## Architecture

### Backend Architecture
- **Modular Structure**: Multi-module Gradle project with separation of concerns
  - `api`: Main Spring Boot application and REST controllers
  - `common`: Shared models, DTOs, and interfaces
  - `client-http`: HTTP client implementations for API consumption
  - `security`: Authentication and authorization components
  - `db-memory`, `db-sqlite`, `db-mongo`: Pluggable database implementations
- **Database Abstraction**: DAO pattern with multiple backend support (H2/SQLite/MongoDB)
- **Core Models**: `Permalink`, `Domain`, `Account` with support for advanced features like activation scheduling and expiry dates
- **Spring Security**: JWT-based authentication with role-based access control
- **Caching**: Caffeine cache integration for performance optimization

### Frontend Architecture
- **Next.js (App Router)**, React 19, TypeScript, Tailwind CSS v4, shadcn/ui (Radix), TanStack Query and Table, react-hook-form + zod
- **Backend for the browser**: the browser never sees the API key. Sign-in stores it in an encrypted httpOnly cookie (`src/lib/session.ts`); `/bff/api/**` (`src/app/bff`) forwards calls to the API with it. `src/proxy.ts` redirects visitors without a session and slides the cookie expiry.
- **Runtime configuration**: `LIKA_API_URL` and `LIKA_SESSION_SECRET` are read at startup (`src/lib/env.ts`, checked in `src/instrumentation.ts`)
- **Key Routes**:
  - `/links`: link management, filtered by domain
  - `/admin/domains`, `/admin/keys`, `/admin/archive`: admin only (`src/app/(app)/admin/layout.tsx`)
  - `/signin`: sign-in with an API key
- `web/AGENTS.md`: this Next.js version differs from older ones; read `web/node_modules/next/dist/docs/` before changing framework code

### Database Selection
The API supports three database backends controlled by Gradle properties:
- Default: In-memory H2 (for development/testing)
- Production options: SQLite (`-PdbSqlite`) or MongoDB (`-PdbMongo`)

### Key Business Logic
- **Permalink Types**: Simple links with optional advanced features (activation scheduling, landing pages, expiry)
- **Multi-tenancy**: Domain-based separation with host-specific permalink management
- **Security Model**: Account-based ownership with admin capabilities for domain/account management