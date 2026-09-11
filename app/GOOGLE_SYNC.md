# Android Google Drive sync

Android sources are in `app/`, not `android/app/`. Settings now has Google Sign-In / Sign Out / Sync Now and status. The implementation uses Play Services Auth and the Drive v3 Java client with a lightweight HttpURLConnection transport. No backend token verification or server auth code is requested, so no Web client ID or client secret is embedded in the APK.

Google Cloud setup:
- Use the same Google Cloud project and consent application as the Web client, with Drive API enabled and the drive.appdata scope configured.
- Register an Android OAuth client for the installed application ID and its signing certificate SHA-1. Release uses `com.joel.gta`. This repository's debug variant uses `com.joel.gta.debug`; register that exact package with the debug certificate, not the release package.
- Obtain the debug SHA-1 with `gradlew.bat signingReport`. Add test users if the consent application is in testing. Use the same Google account on Web and Android.

Synchronization:
- Existing account rehydration and sign-in pull and validate `gtar_songbook_sync.json` from `appDataFolder` before uploading. No sign-in UI is launched automatically.
- Room version 7 migrates existing songs and setlists in place, adding persistent UUID-style sync IDs and BPM. Local numeric IDs and foreign keys remain device-local.
- Payloads use the actual Web `rawContent` and `setlists[].songs` reference schema. `content` and `songIds` input aliases are supported; uploads also include `songIds` alongside the authoritative Web references.
- Room changes debounce for 1.5 seconds. Network restoration retries synchronization. All networking is off the main thread and outside Room transactions. Incoming changes are applied in one transaction after checking for intervening local edits.
- Subsequent sync uses an in-memory three-way baseline; differing edits to the same record stop sync. Initial sign-in uses cloud records for matching IDs while retaining local-only records. Permanent deletion is propagated when a baseline is available; soft-deleted songs are preserved as tombstones.
- Metadata queries explicitly request id/name/version/modifiedTime/md5Checksum. Uploads recheck the exact file ID and revision, preferring version with modifiedTime and checksum fallbacks. This is optimistic concurrency, not an atomic cross-device lock. Concurrent first-time file creation can produce duplicates, which are detected and rejected on later discovery.
- Room cannot represent repeated occurrences of the same song in one setlist (its existing composite primary key permits one membership). Such backups fail validation without modifying Room; they are never silently truncated.
- Web display preferences are retained in cloud payloads but do not replace Android display settings. Reauthorization-required errors leave local editing available; sign out and sign in again to grant access.

Verification: `gradlew.bat assembleDebug testDebugUnitTest`. Live Google consent, account authorization and Android-to-Web network tests require a configured Google Cloud OAuth application and a device with Google Play services.

References: https://developers.google.com/workspace/drive/api/guides/appdata and https://developers.google.com/workspace/drive/api/reference/rest/v3/files
