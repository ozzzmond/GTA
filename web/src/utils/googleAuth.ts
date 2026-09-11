export const GOOGLE_SCOPES = 'openid email profile https://www.googleapis.com/auth/drive.appdata'
const KEY = 'gtar_google_session'
export interface GoogleSession { token: string; expiresAt: number; user: { sub: string; email: string; picture?: string } }
interface TokenResponse { access_token: string; expires_in: number; scope: string; error?: string }
interface TokenClient { requestAccessToken(options: { prompt: string }): void }
interface GIS { accounts: { oauth2: { initTokenClient(config: { client_id: string; scope: string; include_granted_scopes: boolean; callback: (response: TokenResponse) => void; error_callback: () => void }): TokenClient } } }
declare global { interface Window { google?: GIS } }
export function validSession(session: GoogleSession | null): session is GoogleSession { return !!session && session.expiresAt > Date.now() + 30000 }
export function readGoogleSession(): GoogleSession | null {
  try {
    const value = JSON.parse(sessionStorage.getItem(KEY) ?? 'null')
    if (validSession(value) && typeof value.token === 'string' && typeof value.user?.sub === 'string' && typeof value.user?.email === 'string') return value
  } catch { /* Storage unavailable or stale. */ }
  return null
}
export function saveGoogleSession(session: GoogleSession | null) {
  try { if (session) sessionStorage.setItem(KEY, JSON.stringify(session)); else sessionStorage.removeItem(KEY) } catch { /* In-memory sign-in still works. */ }
}
let loading: Promise<void> | undefined
export function loadGoogleIdentity(): Promise<void> {
  if (window.google) return Promise.resolve()
  if (!loading) loading = new Promise<void>((resolve, reject) => {
    const script = document.createElement('script')
    script.src = 'https://accounts.google.com/gsi/client'
    script.async = true
    const timeout = setTimeout(() => { script.remove(); loading = undefined; reject(new Error('Google sign-in unavailable. Try again.')) }, 15000)
    script.onload = () => { clearTimeout(timeout); resolve() }
    script.onerror = () => { clearTimeout(timeout); script.remove(); loading = undefined; reject(new Error('Google sign-in unavailable. Local editing is available.')) }
    document.head.appendChild(script)
  })
  return loading
}
// Call only from a user gesture. Rehydration never opens an OAuth popup.
export function requestGoogleSession(clientId: string): Promise<GoogleSession> {
  return new Promise((resolve, reject) => {
    if (!window.google) { reject(new Error('Google sign-in is still loading. Try again.')); return }
    const client = window.google.accounts.oauth2.initTokenClient({ client_id: clientId, scope: GOOGLE_SCOPES, include_granted_scopes: false,
      error_callback: () => reject(new Error('Sign-in cancelled or popup blocked.')),
      callback: async response => {
        try {
          if (response.error || !response.access_token || !Number.isFinite(Number(response.expires_in))) throw new Error('Google sign-in failed.')
          if (!response.scope.split(' ').includes('https://www.googleapis.com/auth/drive.appdata')) throw new Error('Allow application data access to enable sync.')
          const expiresAt = Date.now() + Number(response.expires_in) * 1000
          const profile = await fetch('https://www.googleapis.com/oauth2/v3/userinfo', { headers: { Authorization: `Bearer ${response.access_token}` }, signal: AbortSignal.timeout(15000) })
          if (!profile.ok) throw new Error('Could not load Google profile.')
          const user = await profile.json()
          if (typeof user.sub !== 'string' || typeof user.email !== 'string') throw new Error('Google profile is missing an email.')
          resolve({ token: response.access_token, expiresAt, user })
        } catch (error) { reject(error) }
      },
    })
    client.requestAccessToken({ prompt: 'select_account' })
  })
}
