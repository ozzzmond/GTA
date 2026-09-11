# Google Drive sync (web)

Set `VITE_GOOGLE_CLIENT_ID` in `web/.env.local` using `web/.env.example`, then restart Vite. In the Google Cloud project, enable Drive API, configure the OAuth consent screen (including test users if in testing), and create a Web application OAuth client with the exact deployed and development origins as authorized JavaScript origins. No client secret belongs in the web app.

The header provides Google sign-in/out and Sync Now. The GIS token client requests only `openid email profile https://www.googleapis.com/auth/drive.appdata`. Valid tokens are cached in sessionStorage for tab reloads. Expiration or a 401 clears the session; reauthorization requires a user click. No background OAuth popups or refresh tokens are used. Sign Out clears this application's session, without signing the user out of Google globally.

Sync uses durable account-scoped journals and create-only Drive revisions. See [sync and recovery semantics](../SYNC_RECOVERY.md) for identity, conflict, restart, recovery export, and concurrency behavior. Songs, trash, and setlists are saved together in the atomic `gtar_library_v1` localStorage entry; older storage keys remain compatibility mirrors. Display settings are included in backups but are not automatically applied from Drive.

Validation: `npm test`, `npm run lint:sync`, and `npm run build`. Tests include a real React mount of the presentation route under AuthGate and a real useDriveSync failed-upload/remount sequence. Network responses are simulated; live OAuth and two-device Drive checks remain separate.

Owner login gate: set `VITE_AUTHORIZED_EMAILS` in the Cloudflare Pages build environment to comma-separated allowed addresses, then rebuild. When unset, the owner defaults to `jlopez3rd@gmail.com`; an explicitly empty value allows nobody. Matching trims whitespace and ignores case, with no substring or domain wildcard matching. `VITE_GOOGLE_CLIENT_ID` remains required, and the hosted origin must be registered as an authorized JavaScript origin in Google Cloud.

The application and all routes, including presentation, mount only after Google userinfo verifies the current token and an allowed, verified email. Valid sessionStorage tokens are reverified on refresh without an OAuth popup. If verification is unavailable, the hosted client remains locked. Sign Out or expiration unmounts the application and clears the cached session; local library data is retained for the next authorized sign-in. The old passcode/quick-unlock screen is no longer used. Logging storage is also paused while locked.

Development bypass is an explicit, in-memory button available only with `import.meta.env.DEV` and a loopback hostname (localhost, 127.0.0.1 or IPv6 loopback). It disables Drive sync and is unavailable in Pages preview/production builds or on LAN hosts.

Security boundary: this is a client-side UI lock. Vite environment values and static assets are public, and a browser owner can alter JavaScript or local storage. Enforce access at Cloudflare Access (including preview URLs) if the hosted application/files must be protected against deliberate bypass. No Cloudflare Access policy or deployment is changed by this implementation.
