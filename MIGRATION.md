# GitHub Actions to GitLab CI Migration Guide

## Overview

This document outlines the migration from GitHub Actions to GitLab CI/CD for the Lika project.

## Key Changes

### 1. Configuration File
- **GitHub Actions**: `.github/workflows/deploy.yml`
- **GitLab CI**: `.gitlab-ci.yml` (root directory)

### 2. Trigger Configuration
- **GitHub Actions**:
  ```yaml
  on:
    push:
      tags: ['v*.*.*']
    workflow_dispatch:
  ```
- **GitLab CI**:
  ```yaml
  rules:
    - if: $CI_COMMIT_TAG =~ /^v\d+\.\d+\.\d+$/
    - if: $CI_PIPELINE_SOURCE == "web"
  ```

### 3. Registry and Image Names
- **GitHub Actions**: Uses `ghcr.io` (GitHub Container Registry)
- **GitLab CI**: Uses `$CI_REGISTRY` (GitLab Container Registry)

### 4. Environment Variables
| GitHub Actions | GitLab CI | Description |
|---------------|-----------|-------------|
| `${{ github.repository }}` | `$CI_PROJECT_PATH` | Project path |
| `${{ github.actor }}` | `$CI_REGISTRY_USER` | Registry username |
| `${{ secrets.GITHUB_TOKEN }}` | `$CI_REGISTRY_PASSWORD` | Registry password |
| `${{ github.ref }}` | `$CI_COMMIT_REF_NAME` | Branch/tag name |
| `${{ github.sha }}` | `$CI_COMMIT_SHA` | Commit hash |

### 5. Job Structure
- **GitHub Actions**: Uses `runs-on` and `steps`
- **GitLab CI**: Uses `image` and `script`

### 6. Docker Configuration
- **GitHub Actions**: Uses external actions like `docker/build-push-action`
- **GitLab CI**: Uses `docker:dind` service with manual Docker commands

### 7. Artifacts and Caching
- **GitHub Actions**: Uses `actions/cache` and `actions/upload-artifact`
- **GitLab CI**: Uses built-in `cache` and `artifacts` keywords

## Job Mappings

### Original GitHub Actions Jobs → GitLab CI Jobs

1. `build-api-memory` → `build-api-memory`
2. `build-api-sqlite` → `build-api-sqlite`
3. `build-api-mongo` → `build-api-mongo`
4. `build-www` → `build-www`
5. `deploy-pages` → `pages`

### Additional GitLab CI Jobs

- `test-api`: Runs Gradle tests for the API
- `test-www`: Runs npm tests for the web application

## Setup Requirements

### GitLab CI Variables (if needed)
These variables are automatically provided by GitLab CI/CD:
- `CI_REGISTRY`: GitLab Container Registry URL
- `CI_REGISTRY_USER`: Registry username
- `CI_REGISTRY_PASSWORD`: Registry password
- `CI_PROJECT_PATH`: Project path
- `CI_COMMIT_TAG`: Git tag (when applicable)
- `CI_COMMIT_SHA`: Git commit hash
- `CI_COMMIT_REF_NAME`: Branch or tag name
- `CI_DEFAULT_BRANCH`: Default branch name

### Docker in Docker (DinD)
GitLab CI uses Docker-in-Docker service for building containers:
```yaml
services:
  - docker:24.0.5-dind
variables:
  DOCKER_HOST: tcp://docker:2376
  DOCKER_TLS_VERIFY: 1
  DOCKER_TLS_CERTDIR: "/certs"
```

### Caching Strategy
GitLab CI uses project-level caching:
```yaml
cache:
  key:
    files:
      - www/package-lock.json  # For npm cache
  paths:
    - www/node_modules/
    - .gradle/  # For Gradle cache
```

## Pages Deployment

### GitHub Pages vs GitLab Pages
- **GitHub Pages**: Uses `actions/deploy-pages@v4`
- **GitLab Pages**: Uses special `pages` job with `public` artifacts directory

## Migration Checklist

- [x] Create `.gitlab-ci.yml` configuration file
- [x] Translate all GitHub Actions jobs to GitLab CI jobs
- [x] Update environment variables and registry references
- [x] Configure Docker-in-Docker for container builds
- [x] Set up caching for dependencies
- [x] Configure GitLab Pages deployment
- [ ] Test pipeline with a sample commit/tag
- [ ] Verify container images are built correctly
- [ ] Confirm Pages deployment works
- [ ] Update project documentation

## Testing the Migration

1. **Push to feature branch**: Test the pipeline without deploying
2. **Create a test tag**: `git tag v0.0.1-test && git push origin v0.0.1-test`
3. **Manual pipeline trigger**: Use GitLab UI to trigger pipeline manually
4. **Verify artifacts**: Check that Docker images and Pages artifacts are created

## Notes

- The GitLab CI configuration includes additional test jobs that weren't in the original GitHub Actions workflow
- Pages deployment is automatic for tags and the default branch
- Container registry authentication is handled automatically by GitLab CI
- All original functionality has been preserved and enhanced with testing capabilities