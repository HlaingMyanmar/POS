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

Without Docker and without a reachable local MySQL configuration, the
integration-test build **fails**. It must never report success with all IT
classes skipped.

## Coverage

| Test | What it proves |
|---|---|
| `FlywayMigrationIT` | Flyway applies through latest migrations; `service_jobs` / `bookings` exist |
| `ServiceJobLifecycleIT` | Booking outdoor convert → settle once (reject double settle) → deliver |
| `BookingConvertHttpIT` | HTTP `POST /bookings` + `/convert-outdoor` against real schema |
| `ProductPhotoSchemaIT` | Flyway ≥115; `product_photos` + `uk_product_photo_slot` exist |
| `ProductPhotosHttpIT` | Create serial product → `PUT /products/{id}/photos` (3 slots) → GET product + serials by product |
| `DeliveryPricingIT` | Delivery pricing rules against the migrated schema |
| `CustomerCatalogIT` | Customer catalog API against real MySQL data |

## Live browser / device E2E

These ITs do **not** replace a full UI E2E. For browser/device:

1. Start the app against a non-production DB (prefer `ser_db_it`).
2. Open the web UI and walk Booking → Job → Final check → Settle → Deliver.
3. Device apps need a reachable `APP_BASE_URL` / API host.

Server must be listening (default `:8080`) before browser automation.

## Web CSV unit tests

Run the focused serializer tests and TypeScript check from
`src/main/resources/ui`:

```powershell
npm run test:csv
npm run lint
```

## Android JVM unit tests

Run both app suites without an emulator:

```powershell
Push-Location technician-app
.\gradlew.bat testDebugUnitTest
Pop-Location

Push-Location customer-app
.\gradlew.bat testDebugUnitTest
Pop-Location
```

The technician suite covers authorization normalization, refresh retry/loop
prevention, inactivity lock/logout thresholds, location heartbeat policy,
AES-GCM envelope integrity, salted PIN hashing, and PIN lockout policy.
The customer suite covers startup authentication/biometric decisions, idle
logout, unauthorized-response handling, cart serialization recovery, and
deposit/remainder payment calculations. It also verifies its AES-GCM session
envelope round trip and tamper rejection.
