# Security

Do **not** paste real passwords, JWT secrets, or keystore passwords into tickets or this file. Secrets belong in environment variables / `.env` / `application-secrets.properties`, never in the WAR.

## Authentication

- Method: **JWT** (HS256) in `Authorization: Bearer`.
- Login: `POST /api/v1/auth/login` with `usernameOremail` + `password`.
- Password storage: **BCrypt** (`ApplicationConfig.PasswordEncoder`).
- `JwtService` expiration: `application.security.jwt.expiration` (milliseconds). Refresh JWT TTL in code: **7 days** (`generateRefreshToken`).
- Login increments `users.token_version` and embeds claim `tv`. Filter rejects mismatch with JSON `{ "error": "SESSION_INVALIDATED", ... }` (HTTP 401). **One login invalidates other devices.**
- `JwtAuthenticationFilter` loads `CustomUserDetailsService` by token subject (email stored as username on `TokenAwareUserDetails`).
- Inactive users: `isEnabled()` is `user.getIsActive()`.
- Session policy: **STATELESS** (`SecurityConfig`). CSRF disabled.

Refresh token is also set as HttpOnly cookie `refreshToken` (Secure, path `/`, maxAge 7 days, SameSite Lax). **There is no refresh controller method.** Frontend still POSTs `/v1/auth/refresh`.

## Authorization

`@EnableMethodSecurity` + `@PreAuthorize` on controllers and/or services.

Authorities from `CustomUserDetailsService`:

- `ROLE_<Role.name>` e.g. `ROLE_ADMINISTRATOR`
- Permission strings e.g. `CAN_ACCESS_SALE_READ`
- If the user has role **ADMINISTRATOR**, **every** `PermissionName` enum value is granted at login (even if the DB role row was edited).

Web UI: `canAccess` also bypasses checks when `user.roles` contains `ADMINISTRATOR` or `ROLE_ADMINISTRATOR`. Role **ADMIN** does **not** get this bypass and is seeded **without** permissions.

The SQL Console is additionally protected by the `ADMIN_QUERY_ENABLED` feature
flag. It defaults to `false` in every profile, including `dev` and `prod`.
Enabling it requires an explicit `ADMIN_QUERY_ENABLED=true`; read/write
permissions are still checked independently in the controller and service.
When disabled, catalog and custom read/write execution are rejected server-side
with HTTP 403, and the web navigation hides the feature.

Android drawer is **not** permission-filtered (technician vs full app). Mutations use `prefs.hasPermission`.

`hasRole('ADMINISTRATOR')` is used for assigning/removing permissions (maps to `ROLE_ADMINISTRATOR`).

## Seeded roles (`RoleName`)

| Role | Seeder behaviour |
|---|---|
| ADMINISTRATOR | All permissions, **reset every boot** |
| ADMIN | Created; permissions empty unless assigned in UI |
| CASHIER | Default list if role is empty (POS counter set) |
| TECHNICIAN | Default list if empty; `CAN_ACCESS_SERVICE_TECHNICIAN_ASSIGN` stripped from names containing TECHNICIAN |
| PURCHASER | Created; permissions empty unless assigned |

Permission catalog: `PermissionName.java` (seeded by `PermissionSeeder`).

## Public HTTP matchers (`SecurityConfig`)

Permit all:

- `/api/v1/auth/**`
- `/ws-clinic/**`, `/ws-native/**`, `/topic/**`
- GET `/api/v1/setup/status`
- POST `/api/v1/setup/initial-admin` (only succeeds when `users` table is empty)
- GET `/api/v1/company-settings`
- GET `/api/v1/app/version`
- GET `/api/v1/app/technician/version`
- POST `/api/v1/scan` (controller requires sale-create permission or scanner pairing token)
- OPTIONS `/**`

Then `/api/**` **authenticated**.  
`anyRequest().permitAll()` — static UI, `/app/**` APK handler, and **possibly actuator** if enabled.

## CORS

`app.cors.allowed-origins` comma-separated. Methods GET POST PUT PATCH DELETE OPTIONS. Headers Authorization, Content-Type, Accept, X-Requested-With, Origin. `allowCredentials=true`.

Some controllers still have `@CrossOrigin(origins = "*")` (conflicts with credentialed CORS if those annotations win — **Needs Confirmation** at runtime).

## HTTPS

Production: Nginx terminates TLS on 443; Spring Boot uses HTTP on `127.0.0.1:8080` (`SSL_ENABLED=false`). No keystore is required to start.

Local optional JVM SSL: `SSL_ENABLED=true` and `SSL_KEYSTORE=file:keystore.p12` (gitignored, excluded from the WAR).

Two historical private-key containers require coordinated rotation and history
purging. Follow [KEYSTORE-INCIDENT-RUNBOOK.md](KEYSTORE-INCIDENT-RUNBOOK.md);
do not rewrite shared history until the repository owner approves the outage,
force-push, and collaborator recovery plan.

## File / data sensitivity

- APK upload directory `app.apk.storage-dir`; served as `/app/**` (`WebMvcConfig`).
- Booking/job/return images stored as LONGTEXT data URLs in DB.
- Backup restore pipes SQL into `mysql` using datasource username/password.
- Android access/refresh tokens are AES-256-GCM encrypted with a non-exportable,
  per-app Android Keystore key. Existing plaintext preference values migrate on
  first access and are removed only after the encrypted value is committed.
- The technician app PIN is stored only as salted PBKDF2-HMAC-SHA256
  (210,000 iterations). Five failed PIN attempts cause a persistent five-minute
  lockout; biometric/device-credential unlock remains available.
- Android backup and device-transfer rules exclude SharedPreferences, databases,
  external storage, and Preferences DataStore security state.

## Protected vs JWT-only

JWT without a permission is still “logged in”. Examples with **no** `@PreAuthorize` on the controller: dashboard stats, chat, company POST, print, voucher settings, manufacturing, barcode, excel export, app-version-settings, scan (public).

`POST /api/v1/scan` broadcasts to `/topic/barcode-scan` only for an authenticated
staff user with `CAN_ACCESS_SALE_CREATE`, or a scanner device presenting the
configured `X-Scanner-Token`.

## Logging

No `logback.xml` in repo. Default Spring Boot logging. `GlobalExceptionHandler` logs unexpected 500s at error and business `RuntimeException` at warn. Backup/print/seeders use SLF4J.

## CSV exports

Web CSV exports use the shared `ui/utils/csv.js` serializer. It escapes CSV
syntax and prefixes untrusted string cells whose first effective character is
`=`, `+`, `-`, or `@`, including leading whitespace/control-character
bypasses. Typed numeric values are not modified.

## Public endpoint rate limits

The backend applies bounded in-memory token buckets before JWT parsing and
controller execution. Limits are per resolved client IP and endpoint:

- staff/customer password login: 10/minute
- refresh: 30/minute; Google login: 20/minute
- customer register: 5/10 minutes
- forgot password: 3/15 minutes; reset password: 5/15 minutes
- initial-admin bootstrap: 5/10 minutes
- paired barcode scanner: 120/minute

Rejected requests return HTTP `429` with `Retry-After`. The cache is capped at
50,000 client/endpoint buckets and expires idle entries. For a multi-instance
deployment, add a shared Redis or edge-proxy limiter because this application
limiter is intentionally process-local.

Production Nginx must overwrite `X-Forwarded-For` with `$remote_addr` and
clear `Forwarded`. Do not use `$proxy_add_x_forwarded_for`: Spring
`forward-headers-strategy=framework` would then treat a client-supplied IP as
`request.getRemoteAddr()` and the login rate-limit key.

## Security findings (do not “fix” in this docs pass)

1. Rotate any credentials that were previously hardcoded (DB password, JWT secret, keystore password, bootstrap admin).
2. Self-signed TLS for LAN if local `SSL_ENABLED=true`.
3. Refresh API missing; refresh token in login JSON **and** cookie **and** frontend `sessionStorage`.
4. Public barcode WebSocket inject.
5. Company settings writable by any authenticated user.
6. Actuator on classpath; `/actuator/health` may be public via `anyRequest().permitAll()` — **Needs Confirmation**.
7. STOMP endpoints permitAll.
8. `assignPermission` **replaces** the whole set.
