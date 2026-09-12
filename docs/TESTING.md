# Integration tests (MySQL + Flyway)

Unit tests (`mvn test`) never run `*IT` classes.

## Backends

1. **Testcontainers** (preferred in CI) — Docker required. No env vars needed.
2. **Local MySQL** — service running on `localhost:3306`, credentials from `application-secrets.properties` / `DB_*` env (same as the app). Uses dedicated DB **`ser_db_it`** (`createDatabaseIfNotExist=true`).

## Run

```powershell
# Local MySQL on this machine (also pass -Dit.useLocalMysql=true for the forked JVM)
$env:IT_USE_LOCAL_MYSQL = "true"
.\mvnw.cmd -Pintegration-test verify "-Dsurefire.skip=true" "-DskipITs=false" "-Dit.useLocalMysql=true"

```

Or:

```powershell
.\scripts\run-integration-tests.ps1
```

Without Docker and without `IT_USE_LOCAL_MYSQL=true`, IT classes skip via JUnit assumptions.

## Coverage

| Test | What it proves |
|---|---|
| `FlywayMigrationIT` | Flyway applies through latest migrations; `service_jobs` / `bookings` exist |
| `ServiceJobLifecycleIT` | Booking outdoor convert → settle once (reject double settle) → deliver |
| `BookingConvertHttpIT` | HTTP `POST /bookings` + `/convert-outdoor` against real schema |
| `ProductPhotoSchemaIT` | Flyway ≥115; `product_photos` + `uk_product_photo_slot` exist |
| `ProductPhotosHttpIT` | Create serial product → `PUT /products/{id}/photos` (3 slots) → GET product + serials by product |

## Live browser / device E2E

These ITs do **not** replace a full UI E2E. For browser/device:

1. Start the app against a non-production DB (prefer `ser_db_it`).
2. Open the web UI and walk Booking → Job → Final check → Settle → Deliver.
3. Device apps need a reachable `APP_BASE_URL` / API host.

Server must be listening (default `:8080`) before browser automation.
