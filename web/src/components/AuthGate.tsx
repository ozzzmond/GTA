import { createContext, useCallback, useContext, useEffect, useRef, useState, type ReactNode } from 'react'
import { authorizedEmail, allowLocalBypass } from '../utils/authPolicy'
import { loadGoogleIdentity, readGoogleSession, requestGoogleSession, saveGoogleSession, validSession, verifyGoogleSession, type GoogleSession } from '../utils/googleAuth'
import { GtaLogoIcon } from './GtaLogoIcon'

interface AuthState { session: GoogleSession | null; signOut: () => void; signIn: () => Promise<void>; ready: boolean; bypass: boolean }
const AuthContext = createContext<AuthState | null>(null)
export function useGoogleAuth() {
  const auth = useContext(AuthContext)
  if (!auth) throw new Error('Authentication boundary is required.')
  return auth
}
export function AuthGate({ children }: { children: ReactNode }) {
  const [session, setSession] = useState<GoogleSession | null>(null)
  const [checking, setChecking] = useState(true)
  const [busy, setBusy] = useState(false)
  const [ready, setReady] = useState(false)
  const [bypass, setBypass] = useState(false)
  const [error, setError] = useState('')
  const epoch = useRef(0)
  const clientId = import.meta.env.VITE_GOOGLE_CLIENT_ID as string | undefined
  const permitted = !!session && validSession(session) && authorizedEmail(session.user.email, import.meta.env.VITE_AUTHORIZED_EMAILS)
  const canBypass = allowLocalBypass(import.meta.env.DEV, window.location.hostname)
  const signOut = useCallback(() => {
    epoch.current++
    saveGoogleSession(null)
    setSession(null); setBypass(false); setChecking(false); setBusy(false); setError('')
  }, [])
  useEffect(() => {
    const generation = ++epoch.current
    const cached = readGoogleSession()
    if (!cached) { saveGoogleSession(null); setChecking(false) }
    else void verifyGoogleSession(cached).then(verified => {
      if (generation !== epoch.current) return
      saveGoogleSession(verified); setSession(verified)
    }).catch(() => {
      if (generation !== epoch.current) return
      saveGoogleSession(null); setError('Unable to verify your session. Please sign in again.')
    }).finally(() => { if (generation === epoch.current) setChecking(false) })
    return () => { epoch.current++ }
  }, [])
  useEffect(() => {
    if (!clientId) return
    let active = true
    void loadGoogleIdentity().then(() => { if (active) setReady(true) }).catch(() => { if (active) setError('Google sign-in is unavailable. Check your connection and reload.') })
    return () => { active = false }
  }, [clientId])
  useEffect(() => {
    if (!session) return
    const expire = () => { if (!validSession(session)) signOut() }
    const timer = setTimeout(signOut, Math.max(0, session.expiresAt - Date.now() - 30000))
    window.addEventListener('focus', expire)
    document.addEventListener('visibilitychange', expire)
    return () => { clearTimeout(timer); window.removeEventListener('focus', expire); document.removeEventListener('visibilitychange', expire) }
  }, [session, signOut])
  useEffect(() => {
    if (!permitted && !(bypass && canBypass)) return
    let active = true
    let pause: (() => void) | undefined
    void import('../utils/logger').then(({ appLogger }) => {
      if (!active) return
      appLogger.resume()
      pause = () => appLogger.suspend()
    })
    return () => { active = false; pause?.() }
  }, [permitted, bypass, canBypass])
  const signIn = async () => {
    if (!clientId || busy) return
    const generation = ++epoch.current
    setBusy(true); setError('')
    try {
      const next = await requestGoogleSession(clientId)
      if (generation !== epoch.current) return
      saveGoogleSession(next); setBypass(false); setSession(next)
    } catch (failure) { if (generation === epoch.current) setError(failure instanceof Error ? failure.message : 'Sign-in failed.') }
    finally { if (generation === epoch.current) { setBusy(false); setChecking(false) } }
  }
  if (permitted || (bypass && canBypass)) return <AuthContext.Provider value={{ session: permitted ? session : null, signOut, signIn, ready, bypass }}>
    {bypass && <div className="bg-amber-500 text-black px-4 py-2 text-sm">Local development bypass · Drive sync disabled <button className="underline ml-3" onClick={signOut}>Exit bypass</button></div>}
    {children}
  </AuthContext.Provider>
  const denied = !!session && !permitted
  return <main className="min-h-screen flex items-center justify-center bg-[#002B36] text-[#FDF6E3] p-6">
    <section className="w-full max-w-md rounded-3xl bg-[#073642] border border-[#1A4A55] p-8 text-center shadow-2xl">
      <GtaLogoIcon className="w-16 h-16 mx-auto text-[#2AA198] mb-4" />
      <h1 className="text-3xl font-bold">GTAR</h1>
      <p className="text-[#93A1A1] mt-2">Songbook &amp; Live Stage Companion</p>
      <h2 className="text-lg font-semibold mt-8">{denied ? 'Unauthorized Access' : 'Owner Access'}</h2>
      <p className="text-sm text-[#93A1A1] mt-2 mb-6">{denied ? `${session.user.email} is not authorized to access this app.` : 'Access is restricted to authorized owners. Sign in with your approved Google account.'}</p>
      {checking ? <p role="status">Verifying your session...</p> : denied ?
        <button className="w-full rounded-xl bg-[#2AA198] text-[#002B36] font-bold py-3" onClick={signOut}>Sign Out / Switch Account</button> :
        <button disabled={!ready || busy} className="w-full rounded-xl bg-[#2AA198] text-[#002B36] font-bold py-3 disabled:opacity-50" onClick={() => void signIn()}>{busy ? 'Signing in...' : 'Sign In with Google'}</button>}
      <p role="status" className="text-sm text-amber-200 mt-4">{error || (!clientId ? 'Google sign-in is not configured. Contact the app owner.' : '')}</p>
      {canBypass && !checking && <button className="mt-6 text-sm underline text-[#93A1A1]" onClick={() => { signOut(); setBypass(true) }}>Continue offline (local development)</button>}
    </section>
  </main>
}
