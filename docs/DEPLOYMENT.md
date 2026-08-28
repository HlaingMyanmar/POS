# Deployment

Source of truth: `pom.xml`, `src/main/resources/application.properties`, `src/main/resources/ui/package.json`, `deploy/nginx.conf`.

There is **no** Dockerfile, **no** CI workflow, **no** systemd unit, and **no** Spring profile (`application-prod.properties` does not exist). Anything not listed here is **Needs Confirmation**.

---

## Build

### Full WAR (API + embedded UI)

From the repo root:

```bash
./mvnw -DskipTests package
```

Windows: `mvnw.cmd -DskipTests package`.

This runs `frontend-maven-plugin` (`install-node-and-npm` Node v22.14.0, `npm install`, `npm run build` in `src/main/resources/ui`). Vite writes to `src/main/resources/static`, then a post-build script copies that tree into `target/classes/static`. Artifact: `target/pos-0.0.1-SNAPSHOT.war` (`artifactId` `pos`, packaging `war`).

`spring-boot-starter-tomcat` is a **compile** dependency (not `provided`). There is **no** `SpringBootServletInitializer`. Local/runtime assumption in this repo is **embedded Tomcat** via `spring-boot:run` or the Spring Boot plugin. Deploying the WAR into an **external** servlet container is **Needs Confirmation**.

### Standalone frontend (Nginx Option A)

From `src/main/resources/ui`:

```bash
npm run build:standalone
```

Uses Vite `--mode standalone --outDir dist`. Comments in `deploy/nginx.conf` say copy `dist/` to `/var/www/sspd` and set `.env.standalone` `VITE_BACKEND_PORT=80`.

---

## Production configuration

Tracked `application.properties` contains no usable runtime secrets. Sensitive values are required through environment variables or the optional Git-ignored `./application-secrets.properties`; setup and rotation steps are in [SECRETS-SETUP.md](SECRETS-SETUP.md).

Set on the server (do not commit real values):

| Key | Why it matters in production |
|---|---|
| `spring.datasource.url` / `username` / `password` | MySQL `ser_db` |
| `application.security.jwt.secret-key` / `expiration` | JWT HMAC |
| `server.ssl.*` | HTTPS on the JVM; keystore `classpath:keystore.p12` |
| `app.cors.allowed-origins` | Needed if the browser origin is not the API origin |
| `app.download.base-url` | Public URL baked into APK download links |
| `app.apk.storage-dir` | Directory must exist |
| `backup.root-directory` and related `backup.*` | File backups (see [BACKUP-RESTORE.md](BACKUP-RESTORE.md)) |
| `server.port` / `server.address` | Default `8080` / `0.0.0.0` |

`server.ssl.enabled` defaults to `true` unless `SSL_ENABLED` overrides it. `deploy/nginx.conf` proxies to **`http://127.0.0.1:8080`**. HTTPS-on-8080 vs HTTP-on-8080 is a mismatch — **Needs Confirmation** which protocol the live process actually speaks.

Checked-in CORS and `app.download.base-url` contain LAN IPs. Replace them for any new host.

---

## Server requirements

From source and the Nginx comments:

- JDK 17
- MySQL (database `ser_db`) reachable from the app process
- The `mysqldump` and `mysql` CLIs on PATH **or** a path stored in backup settings (restore/import)
- Optional: Nginx, if using `deploy/nginx.conf`
- Writable directories for `backup.root-directory` (default `./Backup`) and `app.apk.storage-dir`

OS, RAM, process supervisor, and TLS termination in front of Nginx: **Needs Confirmation**.

---

## Deployment (what exists in the repo)

`deploy/nginx.conf` is the only deployment artifact.

Comments describe **Option A**:

1. `npm run build:standalone`
2. Copy `dist/` to `root` (`/var/www/sspd` in the sample)
3. Start the backend on port 8080
4. Install the file as `/etc/nginx/conf.d/sspd.conf` (or sites-enabled)
5. `nginx -t` and reload Nginx

Nginx sample:

- `listen 80`
- `server_name 192.168.20.248` — change this
- `location /api/` → `http://127.0.0.1:8080/api/`
- `location /ws-clinic/` → `http://127.0.0.1:8080/ws-clinic/` with WebSocket upgrade, `proxy_read_timeout 3600s`
- SPA `try_files` for HashRouter
- Static asset cache 7 days

There is **no** `/ws-native/` location in this Nginx file (Android native WebSocket). There is **no** TLS server block.

How the WAR/process is copied onto the host: **Needs Confirmation**.

---

## Start / stop / restart

No systemd, Windows service, or Docker Compose file is in the repository.

Embedded process (what the Maven plugin supports):

```bash
./mvnw spring-boot:run
```

or run the packaged WAR with a JDK 17 `java` command — exact flags: **Needs Confirmation**.

Stop/restart: **Needs Confirmation** (kill the Java process / whatever supervisor ops use).

Nginx: `sudo nginx -t && sudo systemctl reload nginx` (from file comments). Whether Nginx is actually installed as a systemd unit: **Needs Confirmation**.

---

## Logs

No `logback.xml` / `log4j2.xml` in the repo. Spring Boot default logging (console). `GlobalExceptionHandler` logs unexpected 500s at error. Backup restore/delete failures are logged in `BackupHistoryController`.

Log file path, rotation, and whether production redirects stdout: **Needs Confirmation**.

---

## Health check

`spring-boot-starter-actuator` is on the classpath. There are **no** `management.*` properties.

Default Spring Boot 3 exposure includes `/actuator/health` over HTTP. `SecurityConfig` ends with `anyRequest().permitAll()`, so that path is **not** behind JWT — **Needs Confirmation** that it is reachable and used as a probe.

There is no custom `/health` controller.

---

## Rollback

No documented artifact registry, previous-WAR keep policy, or DB migration down-script.

Practical rollback implied by the code:

- Keep a previous `pos-*.war` / `dist/` copy and redeploy it (**Needs Confirmation** of ops procedure).
- Schema is `ddl-auto=update` plus one-way `*SchemaMigration` runners — **rolling the app back does not roll the schema back**.
- Before a restore-from-backup, the app takes a SAFETY dump (see [BACKUP-RESTORE.md](BACKUP-RESTORE.md)).

---

## Documentation findings (deployment)

- WAR packaging without `SpringBootServletInitializer`; Tomcat starter is not `provided`.
- Nginx proxies **HTTP** to 8080 while checked-in properties enable **HTTPS** on 8080.
- Nginx sample has no `/ws-native/` proxy.
- `backup.legacy-settings-scheduler.enabled` appears in properties; **no Java class reads it**.
- Default Vite proxy target is `http://localhost:8080` while the API is configured for SSL — set `VITE_DEV_PROXY_TARGET=https://localhost:8080` for local proxying (see [TROUBLESHOOTING.md](TROUBLESHOOTING.md)).
