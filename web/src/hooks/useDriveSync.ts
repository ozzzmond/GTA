import { useCallback, useEffect, useRef, useState } from 'react'
import { createBackupPayload } from '../utils/jsonBackup'
import { clearDriveSession, DriveSyncError, pullCloudBackup, pushCloudBackup } from '../utils/driveSync'
import { validSession } from '../utils/googleAuth'
import { useGoogleAuth } from '../components/AuthGate'
import { initializeSyncLibrary, mergeSyncLibrary, type SyncLibrary } from '../utils/syncMerge'

export function useDriveSync(library: SyncLibrary, apply: (library: SyncLibrary) => void) {
  const { session, signOut: lockApp, signIn, ready } = useGoogleAuth()
  const [busy, setBusy] = useState(false)
  const [status, setStatus] = useState(import.meta.env.VITE_GOOGLE_CLIENT_ID ? 'Local changes are saved on this device.' : 'Google sync is not configured. Local editing is available.')
  const latest = useRef({ library, apply, session })
  latest.current = { library, apply, session }
  const generation = useRef(0)
  const running = useRef(false)
  const queued = useRef(false)
  const initialized = useRef(false)
  const baseline = useRef<SyncLibrary | null>(null)
  const signOut = useCallback(() => {
    generation.current++
    if (latest.current.session) clearDriveSession(latest.current.session.token)
    latest.current.session = null
    lockApp()
    initialized.current = false
    baseline.current = null
    setStatus('Signed out. Local editing is available.')
  }, [lockApp])
  useEffect(() => {
    if (!session) return
    const timer = setTimeout(() => { signOut(); setStatus('Google session expired. Sign in to resume sync.') }, Math.max(0, session.expiresAt - Date.now() - 30000))
    return () => clearTimeout(timer)
  }, [session, signOut])
  useEffect(() => () => {
    generation.current++
    const token = latest.current.session?.token
    if (token) clearDriveSession(token)
    latest.current.session = null
    queued.current = false
  }, [])
  const syncNow = useCallback(async () => {
    const auth = latest.current.session
    if (!auth) return
    if (running.current) { queued.current = true; return }
    if (!validSession(auth)) { signOut(); setStatus('Google session expired. Sign in to resume sync.'); return }
    if (!navigator.onLine) { setStatus('Offline. Local changes will sync when connected.'); return }
    const epoch = generation.current
    running.current = true
    setBusy(true)
    setStatus('Syncing...')
    try {
      const pullStarted = latest.current.library
      const cloud = await pullCloudBackup(auth.token)
      if (epoch !== generation.current) return
      if (!initialized.current && cloud) {
        const restored = initializeSyncLibrary(pullStarted, cloud)
        // Retain edits made while the initial download was in flight.
        const current = mergeSyncLibrary(latest.current.library, restored, pullStarted)
        baseline.current = { songs: cloud.songs, setlists: cloud.setlists }
        initialized.current = true
        latest.current.library = current
        latest.current.apply(current)
        queued.current = true
        setStatus('Cloud library restored. Local changes are saved on this device.')
        return
      }
      initialized.current = true
      const before = latest.current.library
      const merged = mergeSyncLibrary(before, cloud, baseline.current)
      await pushCloudBackup(auth.token, createBackupPayload(merged.songs, merged.setlists))
      if (epoch !== generation.current) return
      // Edits made during upload are reconciled against its input, never replaced by a stale snapshot.
      const current = mergeSyncLibrary(latest.current.library, merged, before)
      baseline.current = merged
      if (JSON.stringify(current) !== JSON.stringify(latest.current.library)) latest.current.apply(current)
      setStatus('Synced with Google Drive.')
    } catch (error) {
      if (epoch !== generation.current) return
      if (error instanceof DriveSyncError && error.status === 401) signOut()
      setStatus(error instanceof Error ? error.message : 'Sync failed. Local changes are saved.')
    } finally {
      running.current = false; setBusy(false)
      if (queued.current) { queued.current = false; setTimeout(() => { void syncNow() }, 1500) }
    }
  }, [signOut])
  useEffect(() => {
    if (session) void syncNow()
  }, [session, syncNow])
  useEffect(() => {
    if (!session) return
    const timer = setTimeout(() => { void syncNow() }, 1500)
    return () => clearTimeout(timer)
  }, [library.songs, library.setlists, session, syncNow])
  useEffect(() => {
    const online = () => { void syncNow() }
    window.addEventListener('online', online)
    return () => window.removeEventListener('online', online)
  }, [syncNow])
  return { session, busy, status, signIn, signOut, syncNow, ready }
}
