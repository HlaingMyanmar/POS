# Local setup

Goal: a new developer can clone the repo, create the database, start the API, start the UI, and log in.

These steps match the **current source**. Production host names and secrets are not copied here.

## 1. Install required software

- JDK 17+
- Maven 3.8+ **or** use the repo wrapper (`mvnw` / `mvnw.cmd`)
- MySQL 8+ (or a MariaDB server compatible with the configured `MariaDBDialect`)
- Node.js 22+ **if** you run the UI with Vite (`npm run dev`). A backend-only Maven build downloads Node v22.14.0 via `frontend-maven-plugin` into `target/`

Confirm:

```bash
java -version
mvn -version
mysql --version
node -v
```

## 2. Clone the repository

```bash
git clone <your-remote-url>
cd POS
```

Remote URL: **Needs Confirmation** (not hardcoded as the canonical origin in source).

## 3. Create the database

```sql
CREATE DATABASE ser_db CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
```

Hibernate `spring.jpa.hibernate.ddl-auto=update` creates/updates tables on startup. Additional columns/tables are applied by `*SchemaMigration` runners.

## 4. Configure the backend

Edit `src/main/resources/application.properties` (there is no backend `.env` file).

Set at least:

| Key | Purpose |
|---|---|
| `spring.datasource.url` | JDBC URL; default DB name is `ser_db` |
| `spring.datasource.username` | DB user |
| `spring.datasource.password` | DB password |
| `application.security.jwt.secret-key` | Base64 JWT HMAC key — **use a unique local value** |
| `application.security.jwt.expiration` | Access token TTL in milliseconds (code default in properties: `86400000` = 24h) |
| `app.cors.allowed-origins` | Comma-separated origins, e.g. `https://localhost:8080` and your Vite origin if you call the API directly |
| `server.ssl.*` | HTTPS; keystore is `classpath:keystore.p12` |
| `app.apk.storage-dir` | Folder for Android APK uploads (must exist if you use version settings) |
| `backup.root-directory` | Backup root (default `./Backup`) |

Do not commit production passwords or JWT secrets.

**HTTPS:** `server.ssl.enabled=true`. Browsers and tools will warn about the self-signed cert. Vite proxy sets `secure: false`.

**Port:** `server.port=8080`, `server.address=0.0.0.0`.

## 5. Backend dependencies and start

From the repo root:

```bash
./mvnw spring-boot:run
```

Windows:

```bat
mvnw.cmd spring-boot:run
```

First run will:

1. Compile Java (Lombok + MapStruct annotation processing)
2. Run `frontend-maven-plugin` (`npm install` + `npm run build` in `ui/`) unless you skip that plugin
3. Start Tomcat with SSL
4. Run seeders and schema migrations

To skip the frontend plugin during API-only work: **Needs Confirmation** (no profile in `pom.xml` currently disables it). A full `spring-boot:run` expects Node to be installable by the plugin.

API is at `https://localhost:8080/api/v1/`.

## 6. Frontend dependencies and start (hot reload)

Use this when you change React code often.

```bash
cd src/main/resources/ui
npm install
npm run dev
```

Vite listens on port **3000**, host `0.0.0.0` (`vite.config.ts`).

Optional files / variables:

| Variable | Used in |
|---|---|
| `VITE_DEV_PROXY_TARGET` | Proxy target (default `http://localhost:8080`) |
| `VITE_API_BASE_URL` | Axios base (dev default `/api`) |
| `VITE_WS_URL` | STOMP URL (dev default `/ws-clinic`) |
| `VITE_BACKEND_PORT` | Used when inferring origin in non-dev builds |
| `GEMINI_API_KEY` | Injected in `vite.config.ts` as `process.env.GEMINI_API_KEY`. **No app page usage found.** Leftover. |

`ui/README.md` still mentions AI Studio / Gemini; treat that file as stale.

LAN: `npm run dev:lan` then open `http://<host-ip>:3000`. Allow the firewall for port 3000 if needed.

## 7. Login / smoke test

1. Open `http://localhost:3000` (Vite) or `https://localhost:8080` (built static UI).
2. Accept the certificate warning if using HTTPS directly.
3. Log in.

`UserSeeder` (`@Order(3)`) creates a LOCAL user **only if** a specific admin email is not already in `users`. Username/password are in that Java file — **do not copy them into docs**. Change the password after first login.

If seed did not run (user already exists), ask a teammate for a non-production account.

Expected after login: dashboard (`GET /api/v1/dashboard/stats`). Sidebar items depend on permissions.

## 8. Optional: MySQL tools for backup

Backup uses `mysqldump`; restore uses `mysql`. Install the MySQL client and put it on `PATH`, or set `mysqldumpPath` in backup settings (see [BACKUP-RESTORE.md](BACKUP-RESTORE.md)).

## 9. Common setup errors

| Symptom | Likely cause | What to check |
|---|---|---|
| Cannot connect to DB | Wrong URL/user/password, MySQL not running | `spring.datasource.*`, `CREATE DATABASE ser_db` |
| Browser `NET::ERR_CERT_AUTHORITY_INVALID` | Self-signed PKCS12 | Expected locally; proceed in browser or use Vite HTTP + proxy |
| CORS error | Origin not in `app.cors.allowed-origins` and not going through Vite/Nginx proxy | Add `https://localhost:3000` only if the UI calls the API origin directly (Vite usually same-origin `/api`) |
| 401 on every API | Missing `Authorization: Bearer` | Complete login; check token in memory |
| Frontend 401 then bounce to login | `POST /api/v1/auth/refresh` is called but **not implemented** | Re-login; access token lasts `jwt.expiration` |
| Maven frontend plugin fails | Node download / npm network | Network access; or run `npm install` in `ui/` once |
| `keystore.p12` missing | File not on classpath | Keep `src/main/resources/keystore.p12` |
| APK upload fails | `app.apk.storage-dir` does not exist | Create the directory |
| Permission denied (403) | Role has no permission | `RoleSeeder`: `ADMIN` and `PURCHASER` start empty unless assigned in Role Management |

Next: [ARCHITECTURE.md](ARCHITECTURE.md), [API.md](API.md), [SECURITY.md](SECURITY.md).
