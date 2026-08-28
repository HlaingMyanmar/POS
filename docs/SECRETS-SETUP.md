# Runtime secrets setup

The application no longer contains usable database, JWT, SSL, or bootstrap-administrator credentials in tracked source files.

## Required values

Set these environment variables before starting the backend:

- `DB_PASSWORD`
- `JWT_SECRET` — Base64-encoded key generated from at least 32 random bytes
- `SSL_KEYSTORE_PASSWORD`

Optional deployment variables include `DB_URL`, `DB_USERNAME`, `SSL_ENABLED`, `SSL_KEYSTORE`, `SSL_KEYSTORE_TYPE`, `SSL_KEY_ALIAS`, and `JWT_EXPIRATION_MS`.

For local Windows operation, copy `application-secrets.properties.example` to `application-secrets.properties` and replace every placeholder. The copied file is ignored by Git:

```powershell
Copy-Item .\application-secrets.properties.example .\application-secrets.properties
```

Environment variables take precedence over the local secrets file. Production secrets should be injected by the process supervisor or secret manager rather than copied into the deployment artifact.

## Generate a JWT secret on Windows

Run this in PowerShell and place the resulting Base64 value in `JWT_SECRET` or `application.security.jwt.secret-key`:

```powershell
$bytes = New-Object byte[] 32
$rng = [Security.Cryptography.RandomNumberGenerator]::Create()
$rng.GetBytes($bytes)
[Convert]::ToBase64String($bytes)
$rng.Dispose()
```

Changing the JWT secret immediately invalidates every existing access and refresh token. Users must sign in again.

## Bootstrap administrator

Administrator creation is disabled by default. For a new empty database only, set:

```properties
app.bootstrap-admin.enabled=true
app.bootstrap-admin.email=admin@example.invalid
app.bootstrap-admin.username=administrator
app.bootstrap-admin.password=REPLACE_WITH_AT_LEAST_12_CHARACTERS
```

After the account has been created, set `app.bootstrap-admin.enabled=false` and remove the bootstrap email, username, and password values. Existing administrators are never reset by the bootstrap process.

## Rotation checklist

1. Stop the backend.
2. Change the MySQL account password and update `DB_PASSWORD`.
3. Generate a new JWT secret and update `JWT_SECRET`.
4. Change or replace the PKCS12 keystore password and update `SSL_KEYSTORE_PASSWORD`.
5. Change the previously seeded administrator password through User Management.
6. Restart the backend and verify login, HTTPS, Android API access, and database backup.
7. Sign in again on web and Android because JWT rotation invalidates old sessions.

Do not delete old keys or passwords until the matching database/keystore operation has succeeded. The old values were previously present in tracked files, so treating them as compromised and rotating them is required even after this source change.
