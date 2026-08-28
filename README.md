# SSPD Service Management (POS)

A full-stack shop system for sales, purchases, inventory, service jobs, accounting, and related operations.

This README is the starting point for a new developer. Read it first, then follow the linked docs.

---

## Purpose

The application is used by a service/repair shop to:

- Sell products (cash, credit, partial payment)
- Buy from suppliers (draft → confirm, purchase orders, returns)
- Track stock, serials, lots, warehouses, and adjustments
- Take device bookings and run service jobs through settle / deliver / rework
- Post double-entry journals and run financial reports
- Control access with roles and permissions

The **web application** is a React SPA served by (or proxied to) a Spring Boot API. A native Android app and a legacy `mobile-app/` tree also exist in this repository; they are not required to run the web stack.

---

## Main modules

| Area | What it covers |
|---|---|
| Auth / RBAC | Login (JWT), users, roles, permissions |
| Masters | Products, brands, categories, units, serials, shelves, warehouses |
| Sales | Invoices, quotations, sale returns, cash drawer |
| Purchases | Purchases, POs, goods receipts, purchase returns, supplier payments, budgets |
| CRM | Customers, credit terms/alerts/payments, suppliers, staff |
| Service | Bookings, service catalog, service jobs |
| Accounting | Chart of accounts, journals, expenses, incomes, payment methods/transactions, period lock |
| Reports | Dashboard, P&L, trial balance, balance sheet, aging, rankings, summaries |
| Ops | Chat, audit log, backup/restore, company settings, print, barcode labels, APK version |

---

## Technology stack

| Layer | Technology (from source) |
|---|---|
| Backend | Spring Boot 3.5.8, Java 17, WAR packaging |
| API | Spring Web, Validation, Security, AOP, WebSocket (STOMP) |
| Persistence | Spring Data JPA, Hibernate, MySQL (`mysql-connector-j` 9.6), MariaDB dialect |
| Auth | JWT (jjwt 0.11.5, HS256), BCrypt |
| Cache | Hibernate L2 (JCache + Caffeine) |
| Mapping | MapStruct 1.5.5, Lombok |
| Excel / PDF | Apache POI 5.3.0, JasperReports 6.21.3, Flying Saucer, Thymeleaf |
| Frontend | React 19, Vite 6, TypeScript, HashRouter, Axios, STOMP/SockJS |
| Reverse proxy | Nginx config in `deploy/nginx.conf` |

There is **no Swagger / springdoc** in this project.

---

## Project structure

```text
POS/
├── pom.xml                          # Maven WAR + frontend-maven-plugin
├── src/main/java/org/sspd/servicemgmt/
│   ├── *options/                    # Feature modules (controller/service/repo/model)
│   ├── authoption/, jwt/, securityConfig/
│   ├── config/                      # Seeders + schema migrations
│   ├── exceptionhandler/
│   └── ServicemgmtApplication.java
├── src/main/resources/
│   ├── application.properties       # Datasource, JWT, SSL, CORS, backup
│   ├── keystore.p12                 # HTTPS PKCS12 (self-signed)
│   └── ui/                          # React + Vite source
├── src/test/java/                   # Focused unit/regression tests
├── deploy/nginx.conf                # Production reverse-proxy example
├── android-app/                     # Native Android (out of web run path)
├── mobile-app/                      # Legacy React Native (out of web run path)
└── docs/                            # Developer handover documentation
```

Frontend lives inside the backend repo. Maven `generate-resources` runs `npm run build` in `src/main/resources/ui` and copies static files into the WAR.

---

## Requirements

| Tool | Version |
|---|---|
| JDK | 17+ |
| Maven | 3.8+ (or `./mvnw` / `mvnw.cmd`) |
| Node.js | 22.14.0 (Maven frontend plugin installs this into `target/` during backend build) |
| MySQL | 8+ (database name `ser_db`) |
| Optional | Nginx (production), `mysqldump` / `mysql` CLI (backup/restore) |

---

## Quick start

1. Create MySQL database `ser_db` (utf8mb4). See [docs/LOCAL-SETUP.md](docs/LOCAL-SETUP.md).
2. Set datasource (and other) values in `src/main/resources/application.properties`. **Do not commit real secrets.**
3. Start backend: `./mvnw spring-boot:run` (Windows: `mvnw.cmd spring-boot:run`).
4. For UI hot-reload: `cd src/main/resources/ui && npm install && npm run dev`.
5. Open `http://localhost:3000` (Vite) or `https://localhost:8080` (embedded UI after Maven build).
6. Log in with an existing user. A default LOCAL admin is created by `UserSeeder` if that email is missing — **rotate that password immediately**; credentials are not documented here.

Backend listens on **HTTPS port 8080** (`server.ssl.enabled=true`). Vite proxies `/api` and `/ws-clinic` to the backend and uses `secure: false` for the self-signed cert.

---

## Backend run

```bash
./mvnw spring-boot:run
```

API base path: `/api/v1/` (except backup history: `/api/backups`).

## Frontend run

```bash
cd src/main/resources/ui
npm install
npm run dev
```

Optional env (Vite): `VITE_API_BASE_URL`, `VITE_WS_URL`, `VITE_BACKEND_PORT`, `VITE_DEV_PROXY_TARGET`. See [docs/LOCAL-SETUP.md](docs/LOCAL-SETUP.md).

## Database setup

```sql
CREATE DATABASE ser_db CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
```

Schema is created/updated by Hibernate `ddl-auto=update` plus CommandLineRunner migrations and seeders on startup.

## Configuration keys

Backend keeps non-secret defaults in `application.properties`. Secrets come from environment variables or the Git-ignored `application-secrets.properties`; see [docs/SECRETS-SETUP.md](docs/SECRETS-SETUP.md). Important key names:

- `spring.datasource.url` / `username` / `password`
- `application.security.jwt.secret-key` / `expiration`
- `server.ssl.*`, `app.cors.allowed-origins`
- `app.download.base-url`, `app.apk.storage-dir`
- `backup.*`

---

## Documentation

| Doc | Contents |
|---|---|
| [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) | System and layer map |
| [docs/LOCAL-SETUP.md](docs/LOCAL-SETUP.md) | Step-by-step local run |
| [docs/DATABASE.md](docs/DATABASE.md) | Entities, keys, relationships |
| [docs/API.md](docs/API.md) | REST + WebSocket catalog |
| [docs/BUSINESS-RULES.md](docs/BUSINESS-RULES.md) | Service-layer flows |
| [docs/SECURITY.md](docs/SECURITY.md) | Auth, RBAC, CORS, findings |
| [docs/SECRETS-SETUP.md](docs/SECRETS-SETUP.md) | Runtime secret setup and rotation |
| [docs/DEPLOYMENT.md](docs/DEPLOYMENT.md) | Build, Nginx, unknowns |
| [docs/BACKUP-RESTORE.md](docs/BACKUP-RESTORE.md) | mysqldump backup/restore |
| [docs/TROUBLESHOOTING.md](docs/TROUBLESHOOTING.md) | Common failures |
| [docs/DEVELOPER-HANDOVER.md](docs/DEVELOPER-HANDOVER.md) | Checklist and findings |

---

## License

Private — SSPD internal use.
