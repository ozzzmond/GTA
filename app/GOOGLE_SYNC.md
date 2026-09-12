# Android Google Drive sync

Android sources are in `app/`, not `android/app/`. Settings now has Google Sign-In / Sign Out / Sync Now and status. The implementation uses Play Services Auth and the Drive v3 Java client with a lightweight HttpURLConnection transport. No backend token verification or server auth code is requested, so no Web client ID or client secret is embedded in the APK.

Google Cloud setup:
- Use the same Google Cloud project and consent application as the Web client, with Drive API enabled and the drive.appdata scope configured.
- Register an Android OAuth client for the installed application ID and its signing certificate SHA-1. Release uses `com.joel.gta`. This repository's debug variant uses `com.joel.gta.debug`; register that exact package with the debug certificate, not the release package.
- Obtain the debug SHA-1 with `gradlew.bat signingReport`. Add test users if the consent application is in testing. Use the same Google account on Web and Android.

Synchronization uses the shared [sync and recovery semantics](../SYNC_RECOVERY.md). Stable sync IDs remain separate from numeric Room IDs. The account journal is committed synchronously before uploads and local replacement; Room checks expected state and applies changes in one transaction. Failed uploads do not replace local records. Settings exposes recovery export and explicit publication of a reconciled device library after a conflict.

Room exports schema 7 to `app/schemas`. Supported migrations are 2->3->4->5->6->7. Tests build populated historical databases using the v2 entities from commit 56f2655 and actual migrations, then open them through the current Room implementation. Unsupported paths fail on open without deleting the database. Preserve the database and reinstall a compatible app to export data; never clear app storage as migration recovery. No v1 migration is asserted or fabricated.

Web display settings are preserved in Drive payloads but do not replace Android display settings. Android sign-out or reauthorization errors leave local editing and presentation available.

Verification: `gradlew.bat assembleDebug testDebugUnitTest`. Live Google consent, account authorization and Android-to-Web network tests require a configured Google Cloud OAuth application and a device with Google Play services.

References: https://developers.google.com/workspace/drive/api/guides/appdata and https://developers.google.com/workspace/drive/api/reference/rest/v3/files
