# Developer handover

Use this checklist after cloning. Details live in the other files under `docs/`. **Do not copy secrets from `application.properties` or `UserSeeder` into tickets.**

Android (`android-app/`) and `mobile-app/` are adjacent clients, not required to run the web stack.

---

## Checklist

### Clone

- [ ] Clone the git remote (**Needs Confirmation** of the canonical URL).
- [ ] JDK 17, Maven (or `mvnw.cmd`), MySQL, Node 22+ if using Vite.

See [LOCAL-SETUP.md](LOCAL-SETUP.md).

### Database

- [ ] `CREATE DATABASE ser_db CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;`
- [ ] Point `spring.datasource.*` at that database.
- [ ] First boot: Hibernate `ddl-auto=update` + `*SchemaMigration` runners + seeders (`PermissionSeeder`, `RoleSeeder`, `UserSeeder`, COA/units).

See [DATABASE.md](DATABASE.md).

### Backend run

- [ ] `mvnw.cmd spring-boot:run` (Windows) or `./mvnw spring-boot:run`.
- [ ] API: `https://localhost:8080/api/v1/` (SSL on). Exception: backup history `/api/backups`.
- [ ] First run also builds the UI via `frontend-maven-plugin`.

### Frontend run

- [ ] `cd src/main/resources/ui && npm install && npm run dev` → `http://localhost:3000`.
- [ ] If the Vite proxy cannot reach the API, set `VITE_DEV_PROXY_TARGET=https://localhost:8080`.

### Authentication

- [ ] `POST /api/v1/auth/login` with `usernameOremail` + `password`. JWT Bearer afterward.
- [ ] Login increments `token_version` (one active session per user).
- [ ] Rotate the seeded LOCAL admin password immediately; credentials stay in source, not here.
- [ ] Roles: `ADMINISTRATOR` has every permission (reset every boot). `ADMIN` / `PURCHASER` start empty.
- [ ] There is **no** `POST /api/v1/auth/refresh` implementation; the SPA still calls it.

See [SECURITY.md](SECURITY.md).

### Main business flows

Read [BUSINESS-RULES.md](BUSINESS-RULES.md) before changing these services:

| Flow | Entry |
|---|---|
| Sale | `SaleService.save` / pay / void |
| Purchase | draft vs confirm; `PurchaseService` |
| Sale return | must reference a sale; qty caps |
| Purchase return | submit → approve → dispatch → settle |
| Stock adjustment | DAMAGE/LOSS/FOUND/CORRECTION |
| Payment / journals | created inside sale/purchase/job services |
| Service job | settle (may create `serviceJobSale`), deliver, rework |
| Warranty | fields on product/serial/lines — no warranty module |

Period lock and credit hold/blacklist block several credit paths.

### Build

- [ ] `./mvnw -DskipTests package` → `target/pos-0.0.1-SNAPSHOT.war`.
- [ ] Optional Nginx UI: `npm run build:standalone` in `ui/`.

See [DEPLOYMENT.md](DEPLOYMENT.md).

### Deployment process

- [ ] Only in-repo artifact: `deploy/nginx.conf` (port 80, `/api/` + `/ws-clinic/` to `127.0.0.1:8080`).
- [ ] Confirm with ops: process supervisor, HTTPS vs HTTP on 8080, host names, rollback of WAR vs schema.
- [ ] External Tomcat (WAR without `SpringBootServletInitializer`): **Needs Confirmation**.

### Backup / restore

- [ ] `mysqldump` / `mysql` on PATH or set `mysqldumpPath` in backup settings.
- [ ] Manual: `POST /api/v1/backup/run-now`. Restore: `POST /api/backups/{id}/restore` (SAFETY dump first).
- [ ] Property cron scheduler is **off** (`backup.property-scheduler.enabled=false`); `BackupSchedulerService` uses DB settings.

See [BACKUP-RESTORE.md](BACKUP-RESTORE.md).

### Known issues (must read)

The list in **Documentation findings** below. Do not “fix” them in a docs-only handover unless product asks.

---

## How to navigate the code

| Want | Look in |
|---|---|
| HTTP mapping | `*Controller` under `src/main/java/org/sspd/servicemgmt/**` |
| Rules | matching `*Service` (`@Transactional`, often a second `@PreAuthorize`) |
| Tables | `*model` / `printingoptions/entity` |
| Auth filter | `jwt/JwtAuthenticationFilter`, `securityConfig/SecurityConfig` |
| Permissions | `PermissionName`, `RoleSeeder`, `CustomUserDetailsService` |
| UI routes | `src/main/resources/ui/App.tsx` (`HashRouter`, `guard()`) |
| Axios | `ui/services/api.ts` (`baseURL` `/api`) |
| Realtime | `WebSocketConfig`, `dataevent/DataEventAspect` |
| Seed / schema | `config/*Seeder`, `config/*SchemaMigration` |

---

## Tests

`src/test/java` — purchase-return workflow/accounting, stock lots, PO locking/receiver, booking conversion, job settle serials, backup gzip helpers, booking template smoke. Not a full regression suite.

```bash
./mvnw test
```

---

## Documentation findings

Collected from source vs docs/comments. These are **not** fixed in this documentation pass.

### Auth / security

1. Frontend `POST /v1/auth/refresh` has no backend mapping.
2. Refresh token returned in JSON, HttpOnly cookie, and `sessionStorage` (`sspd_refresh`).
3. Secrets and a default admin password live in git (`application.properties`, `UserSeeder`). Rotate; do not paste values here.
4. `POST /api/v1/scan` is `permitAll` and publishes `/topic/barcode-scan`.
5. GET company-settings is public; POST is JWT without a permission.
6. STOMP `/ws-clinic` and `/ws-native` are `permitAll`.
7. `anyRequest().permitAll()` after `/api/**` authenticated — static UI, `/app/**` APK, and possibly `/actuator/health`.
8. Dual `@PreAuthorize` (controller and/or service); some modules (customer/staff/warehouse) annotate the service only.
9. `PUT` role permissions **replaces** the entire set.
10. Role **ADMIN** is not the ADMINISTRATOR bypass.

### API / clients

11. Backup history uses `/api/backups`, not `/api/v1/…`.
12. Manufacturing complete/cancel: authenticated, **no** permission annotation.
13. Android navigation is technician vs full app, not the permission catalog.
14. Nginx sample has no `/ws-native/` location.

### Data / ops

15. `ddl-auto=update` in checked-in properties.
16. WAR packaging, embedded Tomcat (not `provided`), no `SpringBootServletInitializer`.
17. Nginx `proxy_pass http://127.0.0.1:8080` vs `server.ssl.enabled=true`.
18. Default Vite proxy target is HTTP while the API is HTTPS.
19. `backup.property-scheduler.enabled=false`; `backup.legacy-settings-scheduler.enabled` is unused Java-side.
20. `BackupSettings.keepDays` is not what `cleanOldBackups` uses (property retention counts).
21. `executeBackup(BackupSettings)` always runs type DAILY.
22. Purchase draft cancel: comment vs soft-cancel implementation.
23. Sale void does not delete the `sales` row.
24. Attachments often LONGTEXT data URLs; APKs and SQL dumps are files.
25. OCR preview is regex on `.txt`/`.csv`, not an ML service. `GEMINI_API_KEY` is defined in Vite and unused by pages.
26. No Swagger, no Docker, no CI, no `logback.xml`.
27. `ui/README.md` (Gemini/AI Studio) is stale relative to this app.

### Unclear (Needs Confirmation)

- Canonical git remote and production hosts.
- Whether live Tomcat speaks HTTP or HTTPS on 8080.
- External servlet-container deploy.
- Actuator used as a health probe.
- Full PO and purchase-return status machine for training (read those services).
- Whether production should require JWT on WebSocket handshake.
- Process supervisor, log files, rollback runbook.

---

## Doc index

| File | Topic |
|---|---|
| [../README.md](../README.md) | Start here |
| [ARCHITECTURE.md](ARCHITECTURE.md) | Layers, clients, WebSocket |
| [LOCAL-SETUP.md](LOCAL-SETUP.md) | Clone to login |
| [DATABASE.md](DATABASE.md) | Entities |
| [API.md](API.md) | HTTP + STOMP |
| [BUSINESS-RULES.md](BUSINESS-RULES.md) | Service flows |
| [SECURITY.md](SECURITY.md) | JWT, RBAC, findings |
| [DEPLOYMENT.md](DEPLOYMENT.md) | Build and Nginx |
| [BACKUP-RESTORE.md](BACKUP-RESTORE.md) | mysqldump |
| [TROUBLESHOOTING.md](TROUBLESHOOTING.md) | Failures |
