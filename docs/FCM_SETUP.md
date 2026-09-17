# Customer app FCM setup

1. Create an Android app in Firebase with package `com.sspd.customer` (and `com.sspd.customer.debug` for debug installs).
2. Put the matching `google-services.json` in `customer-app/app/`. Gradle applies `com.google.gms.google-services` 4.5.0 from the project and app `build.gradle.kts` files.
3. In Firebase Console → Project settings → Service accounts → Generate new private key. Save it as `secrets/firebase-adminsdk.json` (gitignored). This is not `google-services.json`.
4. Optional: set `APP_FCM_CREDENTIALS_FILE` if the file is not at `./secrets/firebase-adminsdk.json`. `APP_FCM_CREDENTIALS_BASE64` may be used instead.
5. Deploy the backend so Flyway applies `V138__customer_push_devices.sql`, then rebuild/install the customer app.

The app registers the current device after login and token refresh, and unregisters it on logout. Order updates are sent through both WebSocket and FCM. Invalid/uninstalled device tokens are disabled automatically.

Never commit `google-services.json` or the service-account JSON to source control.

## Technician app (background / closed APK)

1. Firebase project `sspd-technician` Android app package `com.sspd.technician`. Put `google-services.json` in `technician-app/app/`.
2. Project settings → Service accounts → Generate new private key. Save as `secrets/firebase-technician-adminsdk.json` on the VPS (gitignored). Set `APP_FCM_TECHNICIAN_CREDENTIALS_FILE`. Do not reuse the customer `google-services.json` or Web Push VAPID key.
3. Rebuild/deploy the WAR, then install a **release** technician APK on a real phone, login, and allow notifications. The app subscribes to topic `technician_staff_{staffId}`.
4. From web POS, assign a job or send Hand Over to that staff. Tray notification should appear with the app closed. Force-stop may still receive FCM on most devices.

## Physical device — background / closed app

1. Install a release/debug APK that includes `google-services.json` on a real phone (emulator FCM is unreliable).
2. Login, grant notification permission, confirm the device token registered (`customer_push_devices`).
3. Leave the app in the background, then have the shop change an order status. Tray notification should appear without opening the app.
4. Force-stop the app (or swipe it away), repeat a status change. Tray notification should still appear.
5. Tap the notification: Order History should open on that order (`orderId` in FCM data + `OPEN_CUSTOMER_ORDER` click).
6. Foreground: WebSocket still updates the list; FCM `onMessageReceived` also shows a local notification.
