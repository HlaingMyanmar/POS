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

Do not commit keystores, passwords, `local.properties`, or generated build directories.