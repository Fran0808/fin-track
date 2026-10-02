# Fintrack

<p align="center">
  <strong>Personal Cashflow Tracking for Digital Wallets & Bank Notifications</strong>
</p>

<p align="center">
  <a href="https://github.com/Fran0808/WalletPulse/actions/workflows/ci.yml">
    <img src="https://github.com/Fran0808/WalletPulse/actions/workflows/ci.yml/badge.svg" alt="CI Status" />
  </a>
  <img src="https://img.shields.io/badge/Java-21%20LTS-ED8B00?logo=openjdk&logoColor=white" alt="Java 21" />
  <img src="https://img.shields.io/badge/Spring%20Boot-4.1-6DB33F?logo=springboot&logoColor=white" alt="Spring Boot 4" />
  <img src="https://img.shields.io/badge/React-19-61DAFB?logo=react&logoColor=black" alt="React 19" />
  <img src="https://img.shields.io/badge/Android-minSdk%2026-3DDC84?logo=android&logoColor=white" alt="Android" />
  <img src="https://img.shields.io/badge/PostgreSQL-17-4169E1?logo=postgresql&logoColor=white" alt="PostgreSQL 17" />
  <img src="https://img.shields.io/badge/Docker-Compose%20v2-2496ED?logo=docker&logoColor=white" alt="Docker Compose" />
  <img src="https://img.shields.io/badge/License-MIT-blue.svg" alt="License: MIT" />
</p>

---

## Overview

FinTrack records personal cashflow from authorized wallet notifications and bank
emails. It shows incoming money, expenses and internal transfers without claiming
to know live bank balances. Listen Service is the repository name.

Start with the [documentation index](docs/README.md), [local development guide](docs/development.md)
or [Docker guide](docker/README.md). Agent working agreements are in [AGENTS.md](AGENTS.md).

---

## Monorepo Architecture

```text
listen-service/
├── api/                  # [Java 21 / Spring Boot 4 + PostgreSQL] Ingestion, Auth, & Analytics REST API
├── android/              # [Kotlin / Jetpack Compose] Push Notification Listener, QR Pairing, Room DB
├── ui/                   # [React 19 + TypeScript + Vite] Financial Dashboard & QR Device Pairing Modal
├── docker/               # Multi-stage Dockerfiles and container deployment guide
├── scripts/              # [PowerShell] Developer automation (smoke tests, APK builder, test suite)
├── .github/              # [DevOps] GitHub Actions CI pipelines & Pull Request templates
├── docs/                 # Architecture, development, contracts and cashflow rules
├── docker-compose.yml    # Consolidated Docker Compose orchestration (PostgreSQL, Adminer, API)
└── .editorconfig         # Unified formatting standards across IDEs and editors
```

---

## Key Features

- **Passive Android Listener**: Intercepts payment notifications via `NotificationListenerService` and parses sender, amount, and timestamp in real time.
- **Instant QR Device Pairing**: Pair an Android device with a web user account via dynamic QR code using Google Play Services Code Scanner (no camera permissions requested).
- **Dual Multi-Tenant Authentication**:
  - Web Dashboard: Google OAuth 2.0 with session lifecycle and JWT Bearer tokens.
  - Android Device: Dedicated device pairing tokens (`X-Device-Token`) bound to the user profile.
- **Per-User Idempotency**: A composite unique constraint on `(transaction_hash, user_id)` rejects repeated hashes for a user. Email and notification hashes differ; cross-source deduplication is not guaranteed. See [cashflow rules](docs/domain/cashflow.md).
- **Interactive Financial Dashboard**: Dynamic cashflow summary cards, daily expense curves, merchant distribution, and filterable transactions table.
- **Containerized DevOps**: Local reproducible environment with Docker Compose, PostgreSQL 17, and Adminer web management.

---

## Quickstart with Docker

### 1. Prerequisites
- [Docker Desktop](https://www.docker.com/products/docker-desktop/) (Windows / macOS) or Docker Engine + Compose v2 (Linux).
- [Node.js 24 LTS](https://nodejs.org/) (for running the React dashboard in development).

### 2. Configuration
Create the environment file if it is missing, then configure your credentials:

```powershell
if (-not (Test-Path -LiteralPath .env)) {
    Copy-Item -LiteralPath .env.example -Destination .env
}
```

Edit `.env` to set your desired `DB_PASSWORD`, `JWT_SECRET`, and optional Google OAuth credentials.

### 3. Start Infrastructure & Backend API
Run Docker Compose from the project root:

```bash
# Start PostgreSQL 17 and Backend API
docker compose up -d

# Optional: Start with Adminer Web UI (http://localhost:8081)
docker compose --profile tools up -d
```

### 4. Start Frontend Dashboard
In a separate terminal, start Vite's development server:

```bash
cd ui
npm ci
npm run dev
```

Open [http://localhost:5173](http://localhost:5173) in your browser.


---

## Developer Automation Scripts

The `scripts/` directory contains PowerShell tools. Review their
[current limitations and data-access behavior](docs/development.md#existing-scripts)
before execution.

| Script | Purpose |
| :--- | :--- |
| [`scripts/verify-all.ps1`](scripts/verify-all.ps1) | Runs all tests across modules (`mvn test`, `npm test`, `npm run build`, `gradlew testDebugUnitTest`). |
| [`scripts/build-apk.ps1`](scripts/build-apk.ps1) | Compiles Android debug APK, copies it to the Desktop, and prints SHA-256 hash. |
| [`scripts/test-api.ps1`](scripts/test-api.ps1) | Smoke-tests REST API security boundaries, QR token verification, and transaction ingestion. |

---
