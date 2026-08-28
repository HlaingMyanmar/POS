# Security

Do **not** paste real passwords, JWT secrets, or keystore passwords into tickets or this file. Values currently live in `application.properties` and `UserSeeder` — rotate them.

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
- GET `/api/v1/company-settings`
- GET `/api/v1/app/version`
- POST `/api/v1/scan`
- OPTIONS `/**`

Then `/api/**` **authenticated**.  
`anyRequest().permitAll()` — static UI, `/app/**` APK handler, and **possibly actuator** if enabled.

## CORS

`app.cors.allowed-origins` comma-separated. Methods GET POST PUT PATCH DELETE OPTIONS. Headers Authorization, Content-Type, Accept, X-Requested-With, Origin. `allowCredentials=true`.

Some controllers still have `@CrossOrigin(origins = "*")` (conflicts with credentialed CORS if those annotations win — **Needs Confirmation** at runtime).

## HTTPS

`server.ssl.enabled=true`, PKCS12 `classpath:keystore.p12`, alias `servicemgmt`. Password is a property — treat as secret.

## File / data sensitivity

- APK upload directory `app.apk.storage-dir`; served as `/app/**` (`WebMvcConfig`).
- Booking/job/return images stored as LONGTEXT data URLs in DB.
- Backup restore pipes SQL into `mysql` using datasource username/password.

## Protected vs JWT-only

JWT without a permission is still “logged in”. Examples with **no** `@PreAuthorize` on the controller: dashboard stats, chat, company POST, print, voucher settings, manufacturing, barcode, excel export, app-version-settings, scan (public).

`POST /api/v1/scan` is **unauthenticated** and broadcasts to `/topic/barcode-scan`.

## Logging

No `logback.xml` in repo. Default Spring Boot logging. `GlobalExceptionHandler` logs unexpected 500s at error and business `RuntimeException` at warn. Backup/print/seeders use SLF4J.

## Security findings (do not “fix” in this docs pass)

1. Secrets and default admin password in source (`application.properties`, `UserSeeder`).
2. Self-signed TLS for LAN.
3. Refresh API missing; refresh token in login JSON **and** cookie **and** frontend `sessionStorage`.
4. Public barcode WebSocket inject.
5. Company settings writable by any authenticated user.
6. Actuator on classpath; `/actuator/health` may be public via `anyRequest().permitAll()` — **Needs Confirmation**.
7. STOMP endpoints permitAll.
8. `assignPermission` **replaces** the whole set.
