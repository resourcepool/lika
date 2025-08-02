# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

Lika is an open-source permalink API consisting of two main components:
- **Backend API** (`api/`): Kotlin/Spring Boot application for permalink management
- **Frontend Web App** (`www/`): Angular application for the user interface

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

### Frontend (Angular)
```bash
# Navigate to web app directory
cd www

# Install dependencies
npm install

# Start development server
npm start
# or
ng serve

# Build for production
npm run build
# or 
ng build

# Run tests
npm test
# or
ng test

# Build and watch for changes
npm run watch
# or
ng build --watch --configuration development
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
- **Angular 20**: Modern Angular application using standalone components
- **Route-based Architecture**: Lazy-loaded components with authentication guards
- **Key Routes**:
  - `/dashboard`: Main permalink management interface
  - `/admin/domains`: Domain administration (protected)
  - `/admin/accounts`: Account management (protected)
  - `/signin`: Authentication page
- **Services**: Dedicated services for API communication (auth, permalink, domain, account, search)
- **Material Design**: Angular Material UI components throughout

### Database Selection
The API supports three database backends controlled by Gradle properties:
- Default: In-memory H2 (for development/testing)
- Production options: SQLite (`-PdbSqlite`) or MongoDB (`-PdbMongo`)

### Key Business Logic
- **Permalink Types**: Simple links with optional advanced features (activation scheduling, landing pages, expiry)
- **Multi-tenancy**: Domain-based separation with host-specific permalink management
- **Security Model**: Account-based ownership with admin capabilities for domain/account management