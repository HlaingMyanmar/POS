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

Schema is owned by Flyway (`spring.jpa.hibernate.ddl-auto=validate`). The database itself must already exist.

## 4. Configure the backend

Copy `.env.example` to `.env` **or** `application-secrets.properties.example` to `application-secrets.properties` in the repo root. Do not commit either file.

Local defaults (no `prod` profile): HTTP on port 8080, Flyway validate, no packaged keystore. Optional local HTTPS: `SSL_ENABLED=true` and `SSL_KEYSTORE=file:keystore.p12` (gitignored, not inside the WAR).

| Variable | Purpose |
|---|---|
| `DB_URL` / `DB_USERNAME` / `DB_PASSWORD` | JDBC connection |
| `JWT_SECRET` | Base64 JWT HMAC key — unique local value |
| `SSL_ENABLED` | Default `false`; Tomcat starts without a keystore |
| `CORS_ALLOWED_ORIGINS` | Comma-separated browser origins |
| `APP_BASE_URL` | Public URL used in APK download links |
| `APP_APK_STORAGE_DIR` | APK upload folder |
| `BACKUP_ROOT_DIRECTORY` | Backup root (default `./Backup`) |
| `BOOTSTRAP_ADMIN_*` | Create the first admin on an empty database only |

Do not commit production passwords or JWT secrets.

**Port:** `SERVER_PORT` default `8080`, `SERVER_ADDRESS` default `0.0.0.0` locally.

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

API is at `http://localhost:8080/api/v1/` when `SSL_ENABLED=false`.

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

1. Open `http://localhost:3000` (Vite) or `http://localhost:8080` (built static UI).
2. Log in with an existing user.

`UserSeeder` is off by default. If the database has **no users**, the web UI asks you to create the first ADMINISTRATOR, then shows the login screen.

If seed did not run (user already exists), ask a teammate for a non-production account.

Expected after login: dashboard (`GET /api/v1/dashboard/stats`). Sidebar items depend on permissions.

## 8. Optional: MySQL tools for backup

Backup uses `mysqldump`; restore uses `mysql`. Install the MySQL client and put it on `PATH`, or set `mysqldumpPath` in backup settings (see [BACKUP-RESTORE.md](BACKUP-RESTORE.md)).

## 9. Common setup errors

| Symptom | Likely cause | What to check |
|---|---|---|
| Cannot connect to DB | Wrong URL/user/password, MySQL not running | `DB_URL` / `DB_USERNAME` / `DB_PASSWORD`, `CREATE DATABASE ser_db` |
| Browser `NET::ERR_CERT_AUTHORITY_INVALID` | Local JVM SSL with a self-signed keystore | Use `SSL_ENABLED=false` or Vite HTTP + proxy |
| CORS error | Origin not in `CORS_ALLOWED_ORIGINS` and not going through Vite/Nginx proxy | Add `http://localhost:3000` only if the UI calls the API origin directly |
| 401 on every API | Missing `Authorization: Bearer` | Complete login; check token in memory |
| Frontend 401 then bounce to login | `POST /api/v1/auth/refresh` is called but **not implemented** | Re-login; access token lasts `jwt.expiration` |
| Maven frontend plugin fails | Node download / npm network | Network access; or run `npm install` in `ui/` once |
| App fails looking for `keystore.p12` | `SSL_ENABLED=true` without `SSL_KEYSTORE` | Set `SSL_ENABLED=false`, or `SSL_KEYSTORE=file:keystore.p12` |
| APK upload fails | `APP_APK_STORAGE_DIR` does not exist | Create the directory |
| Permission denied (403) | Role has no permission | `RoleSeeder`: `ADMIN` and `PURCHASER` start empty unless assigned in Role Management |

Next: [ARCHITECTURE.md](ARCHITECTURE.md), [API.md](API.md), [SECURITY.md](SECURITY.md).
