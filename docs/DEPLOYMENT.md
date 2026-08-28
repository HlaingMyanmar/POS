# Deployment

Source of truth: `pom.xml`, `src/main/resources/application.properties`, `application-prod.properties`, `.env.example`, `deploy/nginx.conf`, `deploy/sspd.service`.

Production architecture:

```text
Internet -- HTTPS 443 --> Nginx -- HTTP 127.0.0.1:8080 --> Spring Boot WAR --> MySQL/MariaDB
```

Spring Boot does **not** terminate TLS in production (`SSL_ENABLED=false`). Secrets are **not** packaged in the WAR.

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

Use profile `prod` and environment variables from `.env.example`. Copy that file to `/opt/sspd/.env` on the VPS. Optional file fallbacks: `./application-secrets.properties` and `/opt/sspd/application-secrets.properties`.

| Environment variable | Purpose |
|---|---|
| `SPRING_PROFILES_ACTIVE=prod` | Loads `application-prod.properties` |
| `DB_URL` / `DB_USERNAME` / `DB_PASSWORD` | MySQL/MariaDB |
| `JWT_SECRET` | Base64 HMAC key, at least 32 random bytes |
| `SSL_ENABLED=false` | No JVM keystore; Nginx terminates HTTPS |
| `APP_BASE_URL` | Public HTTPS origin used in APK download links |
| `APP_APK_STORAGE_DIR` | Default `/opt/sspd/apk` |
| `BACKUP_ROOT_DIRECTORY` | Default `/opt/sspd/Backup` |
| `CORS_ALLOWED_ORIGINS` | Comma-separated browser origins |
| `SERVER_ADDRESS=127.0.0.1` / `SERVER_PORT=8080` | Bind localhost only behind Nginx |

Flyway stays enabled; `spring.jpa.hibernate.ddl-auto=validate`; `spring.flyway.clean-disabled=true`. This does **not** drop or recreate the database.

---

## Server requirements

- Ubuntu VPS, JDK 17, Nginx, MySQL/MariaDB (`ser_db`)
- `mysqldump` / `mysql` on PATH (or `MYSQLDUMP_PATH`)
- Writable `/opt/sspd/apk` and `/opt/sspd/Backup`

---

## Ubuntu VPS (WAR behind Nginx)

Create the service user and directories:

```bash
sudo useradd --system --home /opt/sspd --shell /usr/sbin/nologin sspd
sudo mkdir -p /opt/sspd/apk /opt/sspd/Backup
sudo cp .env.example /opt/sspd/.env
sudo nano /opt/sspd/.env
sudo chmod 600 /opt/sspd/.env
sudo chown -R sspd:sspd /opt/sspd
```

Copy the WAR and unit file:

```bash
sudo cp target/pos-0.0.1-SNAPSHOT.war /opt/sspd/
sudo cp deploy/sspd.service /etc/systemd/system/sspd.service
sudo systemctl daemon-reload
sudo systemctl enable --now sspd
```

Install Nginx TLS proxy (`deploy/nginx.conf`):

```bash
sudo apt install nginx
sudo cp deploy/nginx.conf /etc/nginx/sites-available/sspd
sudo nano /etc/nginx/sites-available/sspd   # set YOUR_DOMAIN
sudo ln -sf /etc/nginx/sites-available/sspd /etc/nginx/sites-enabled/sspd
sudo rm -f /etc/nginx/sites-enabled/default
sudo certbot --nginx -d YOUR_DOMAIN
sudo nginx -t && sudo systemctl reload nginx
```

Start command (manual, equivalent to the systemd unit):

```bash
sudo -u sspd bash -lc 'cd /opt/sspd && set -a && source /opt/sspd/.env && set +a && java -Dspring.profiles.active=prod -jar /opt/sspd/pos-0.0.1-SNAPSHOT.war'
```

If systemd `EnvironmentFile` is used, this is enough:

```bash
java -Dspring.profiles.active=prod -jar /opt/sspd/pos-0.0.1-SNAPSHOT.war
```

---

## Start / stop / restart

```bash
sudo systemctl start sspd
sudo systemctl stop sspd
sudo systemctl restart sspd
sudo systemctl status sspd
```

Nginx: `sudo nginx -t && sudo systemctl reload nginx`.

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

- Keep a previous `pos-*.war` copy and redeploy it.
- Schema is Flyway-owned (`ddl-auto=validate`) — **rolling the app back does not roll the schema back**.
- Before a restore-from-backup, the app takes a SAFETY dump (see [BACKUP-RESTORE.md](BACKUP-RESTORE.md)).

---

## Documentation findings (deployment)

- WAR packaging without `SpringBootServletInitializer`; Tomcat starter is not `provided`. Run with `java -jar`.
- Production TLS is on Nginx; Spring Boot HTTP on 127.0.0.1:8080.
- `backup.legacy-settings-scheduler.enabled` appears in properties; **no Java class reads it**.
