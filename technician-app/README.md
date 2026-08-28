# SSPD Technician

Standalone Android application for SSPD field technicians.

## Requirements

- Android Studio with Android SDK 35
- JDK 17
- Android device or emulator running Android 8.0 (API 26) or newer

## Build

```powershell
.\gradlew.bat assembleDebug
```

Debug APK output:

`app/build/outputs/apk/debug/app-debug.apk`

## Configuration

The default API URL is defined by `DEFAULT_BASE_URL` in `app/build.gradle.kts`.
Update it for the deployment environment before producing a release build.

For release signing, create `local.properties` (this file is ignored by Git):

```properties
sdk.dir=C\:\\path\\to\\Android\\Sdk
KEYSTORE_PATH=../sspd-release.keystore
KEYSTORE_PASSWORD=your-password
KEY_ALIAS=sspd
KEY_PASSWORD=your-password
```

`assembleRelease` fails if the keystore file or passwords are missing.

Do not commit keystores, passwords, `local.properties`, or generated build directories.

## Release and in-app update

1. Increment `versionCode` (and optionally `versionName`) in `app/build.gradle.kts`.
2. Set `DEFAULT_BASE_URL` to the production HTTPS server.
3. Run `.\gradlew.bat assembleRelease`.
4. Output: `app/build/outputs/apk/release/app-release.apk`.
5. In the web admin, open **Settings → App Version → Technician**.
6. Upload the APK (stored as `technician.apk`), set version code **greater than** installed devices, write changelog, then Save.
7. Technicians receive an update dialog on Home, or can open **Software Update** from the drawer.

The technician app checks `GET /api/v1/app/technician/version` and downloads `/app/technician.apk`. It does not use the POS Manager APK.
