# Troubleshooting

Causes below are tied to this repository’s configuration and code. If a symptom is not listed, it was not confirmed here.

---

## Cannot start / cannot connect to MySQL

**Symptoms:** datasource errors at boot; `CommunicationsException`; unknown database.

**Check**

- MySQL is running and `CREATE DATABASE ser_db` was executed (`utf8mb4`).
- `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` in `.env` or `application-secrets.properties`.
- JDBC URL database name must match (default `ser_db`).

Hibernate `ddl-auto=update` will not create the **database**, only tables inside it.

---

## HTTPS / certificate errors

**Symptoms:** `NET::ERR_CERT_AUTHORITY_INVALID`; curl SSL errors; app fails to start because a keystore is missing.

Default is `SSL_ENABLED=false` (HTTP). The WAR starts without `keystore.p12`. Production TLS belongs on Nginx.

If you enable local JVM SSL (`SSL_ENABLED=true`), point `SSL_KEYSTORE` at a gitignored file such as `file:keystore.p12`. Missing keystore → SSL context fails at startup.

**UI work:** Vite on HTTP `:3000` proxies to `http://localhost:8080`.

---

## Vite proxy cannot reach the API

Default `VITE_DEV_PROXY_TARGET` is **`http://localhost:8080`**, which matches `SSL_ENABLED=false`.

If you turn local JVM SSL on, set `VITE_DEV_PROXY_TARGET=https://localhost:8080` (see [LOCAL-SETUP.md](LOCAL-SETUP.md)). `secure: false` accepts a self-signed cert.

---

## CORS errors

`app.cors.allowed-origins` is a comma-separated allow-list with `allowCredentials=true`.

Vite and Nginx same-origin `/api` avoid CORS. Calling `https://host:8080` from another origin requires that origin on the list.

Some controllers also have `@CrossOrigin(origins = "*")`. Which annotation wins at runtime: **Needs Confirmation**.

---

## Login works then every request 401 / bounce to login

1. Access token missing (`Authorization: Bearer`) — frontend keeps it in memory (`services/api.ts`).
2. Another login incremented `users.token_version` → filter returns `SESSION_INVALIDATED`.
3. User `isActive=false`.
4. Axios interceptor POSTs `/v1/auth/refresh`. **That controller method does not exist.** After access-token expiry (`jwt.expiration`, properties default 24h) the UI cannot refresh — user must log in again.

---

## 403 Forbidden

`@PreAuthorize` failed. `GlobalExceptionHandler` maps `AccessDeniedException` to 403.

Role **ADMIN** and **PURCHASER** are seeded **without** permissions. **ADMINISTRATOR** is granted every `PermissionName` at login and is reset on every boot.

Web UI also bypasses sidebar/route guards for role name `ADMINISTRATOR`. Android drawer is not permission-filtered.

---

## Sale / purchase / job rejected

Read the exception message (often HTTP 400 `ErrorResponse`). Common code paths:

| Message / behaviour | Where |
|---|---|
| Details/customer required, discount cap, credit hold/blacklist, date permissions | `SaleService.save` |
| Serial count ≠ qty, duplicate invoice, period locked, duplicate 15s guard | `PurchaseService` |
| Cannot return more than sold | `SaleReturnService` |
| Credit hold on due > 0 | Job settle / credit sale |
| “Sale detail updates are not supported” | `SaleService` update |
| Draft-only purchase update | `PurchaseService` |

Period lock: `periodGuard.assertOpen` on purchase confirm/cancel (and similar). Unlock via accounting period APIs if you have permission.

---

## Stock / serial mismatches

- Confirmed purchase of serial products requires one serial per qty.
- Stock adjustment: `|qtyChange|` must equal serial count when serials are sent; DAMAGE/LOSS cannot drive stock below 0.
- Sale void restores lots; sale update cannot change lines.

If UI and DB disagree after a restore, restart the app (L2 cache); restore does not flush Hibernate cache in code.

---

## Backup failed / restore cancelled

| Symptom | Likely cause |
|---|---|
| `runNow` success=false / history FAILED | `mysqldump` missing, bad path in settings, auth failure (stderr stored on history) |
| “Backup verification failed” | non-zero exit, empty file, or gzip unreadable |
| “Safety backup failed; restore cancelled” | dump before restore failed — fix mysqldump first |
| “Only .sql.gz, .sql or .sqlbackup” | wrong upload extension |
| “Backup file does not exist” | history row left after file delete/retention |
| Import 500 | `mysql` client missing or SQL error |

Set `mysqldumpPath` in backup settings to the full binary path. Restore needs the sibling `mysql` binary.

Working directory matters: default `./Backup` is relative to the process CWD.

---

## WebSocket / live lists not updating

Browser uses SockJS `/ws-clinic`. Nginx must proxy that location with `Upgrade` headers (`deploy/nginx.conf` does). Timeout in the sample is 3600s.

Android uses `/ws-native`. The sample Nginx file **does not** proxy `/ws-native/`.

STOMP endpoints are `permitAll`. Topics include `/topic/data-events`, `/topic/sales`, `/topic/barcode-scan`, etc.

`POST /api/v1/scan` is public and broadcasts `/topic/barcode-scan`.

---

## Maven frontend plugin fails

`generate-resources` downloads Node **v22.14.0** into `target/` and runs `npm install` / `npm run build`. Network blocks or npm registry issues fail the whole `spring-boot:run` / `package`.

There is **no** Maven profile in `pom.xml` to skip the plugin (**Needs Confirmation** of a local workaround).

---

## APK upload fails

`app.apk.storage-dir` must exist. Multipart limits: `200MB`. POS Manager downloads `/app/servicemgmt.apk`; technician downloads `/app/technician.apk`. Download URL uses `app.download.base-url`.

Technician in-app updates require a signed `assembleRelease` APK uploaded from **Settings → App Version → Technician**, with version code greater than `technician-app/app/build.gradle.kts`.

---

## Actuator / health

`/actuator/health` may be open because `anyRequest().permitAll()`. If a probe 404s, management exposure was left at Spring Boot defaults or a proxy does not forward `/actuator` — **Needs Confirmation**.

---

## Nginx 502 / empty API

Sample proxies to `http://127.0.0.1:8080`. If the JVM only listens with SSL, HTTP proxy_pass fails. Align protocol with `server.ssl.enabled` (**Needs Confirmation** on the live host).

`server_name` in the sample is a LAN IP; wrong name is not usually 502, but TLS/vhost mistakes can look like “site not loading”.

---

## Tests

`./mvnw test` runs ~12 focused tests (purchase return, lots, booking conversion, backup gzip helpers, etc.). There is no documented full E2E suite.
