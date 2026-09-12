import { mergeSyncLibrary, type SyncLibrary } from './syncMerge'

interface Journal {
  version: 1
  baseline: SyncLibrary | null
  pending?: { before: SyncLibrary; merged: SyncLibrary; acknowledged: boolean }
}
const key = (account: string) => `gtar_sync_v1:${account}`
const ownerKey = 'gtar_sync_library_owner'
const MAX_RECOVERY_SNAPSHOTS = 2

function pruneRecoverySnapshots(storage: Storage, account: string, maxAllowed: number) {
  const prefix = `gtar_sync_recovery:${account}:`
  const existingKeys: string[] = []
  for (let i = 0; i < storage.length; i++) {
    const k = storage.key(i)
    if (k?.startsWith(prefix)) existingKeys.push(k)
  }
  while (existingKeys.length > maxAllowed) {
    const oldest = existingKeys.shift()
    if (oldest) {
      try { storage.removeItem(oldest) } catch { /* ignore storage error on remove */ }
    }
  }
}

/** Storage failures stop sync before network writes or local replacement. No tokens are stored. */
export function openSyncJournal(account: string, local: SyncLibrary, storage: Storage = localStorage, resumePending = true) {
  if (!account) throw new Error('Verified account ID is required for sync.')
  const owner = storage.getItem(ownerKey)
  if (owner && owner !== account) throw new Error('This device library belongs to another Google account. Sign in to that account to sync; export its backup before moving data to another account.')
  storage.setItem(ownerKey, account)
  const raw = storage.getItem(key(account))
  const journal: Journal = raw ? JSON.parse(raw) : { version: 1, baseline: null }
  if (journal.version !== 1) throw new Error('Unsupported sync recovery state. Export device data before recovery.')
  const resumed = resumePending && journal.pending?.acknowledged
    ? mergeSyncLibrary(local, journal.pending.merged, journal.pending.before) : local
  const baseline = journal.pending?.acknowledged ? journal.pending.merged : journal.baseline
  return {
    local: resumed, baseline,
    archive(remote: SyncLibrary | null) {
      // Prune older recovery entries to keep at most MAX_RECOVERY_SNAPSHOTS - 1 before adding new
      pruneRecoverySnapshots(storage, account, Math.max(0, MAX_RECOVERY_SNAPSHOTS - 1))
      const prefix = `gtar_sync_recovery:${account}:`
      const recovery = `${prefix}${crypto.randomUUID()}`
      try {
        storage.setItem(recovery, JSON.stringify({ local, remote, journal }))
      } catch (e) {
        // If quota exceeded, prune down to 1 (preserving the last durable copy) and try once more
        console.warn('Initial recovery snapshot save failed. Purging older snapshots down to last durable copy.', e)
        pruneRecoverySnapshots(storage, account, 1)
        try {
          storage.setItem(recovery, JSON.stringify({ local, remote, journal }))
        } catch (retryErr) {
          throw new Error('Unable to persist recovery snapshot to storage (quota exceeded). Sync stopped safely.', { cause: retryErr })
        }
      }
    },
    prepare(before: SyncLibrary, merged: SyncLibrary) {
      journal.baseline = baseline
      journal.pending = { before, merged, acknowledged: false }
      try {
        storage.setItem(key(account), JSON.stringify(journal))
      } catch {
        // Free older recovery snapshots down to 1 (preserving last durable copy) if quota is hit
        pruneRecoverySnapshots(storage, account, 1)
        storage.setItem(key(account), JSON.stringify(journal))
      }
    },
    complete() {
      if (!journal.pending?.acknowledged) throw new Error('Upload is not acknowledged')
      journal.baseline = journal.pending.merged
      delete journal.pending
      try {
        storage.setItem(key(account), JSON.stringify(journal))
      } catch {
        pruneRecoverySnapshots(storage, account, 1)
        storage.setItem(key(account), JSON.stringify(journal))
      }
    },
    acknowledge() {
      if (!journal.pending) throw new Error('Missing prepared sync journal')
      journal.pending.acknowledged = true
      try {
        storage.setItem(key(account), JSON.stringify(journal))
      } catch {
        pruneRecoverySnapshots(storage, account, 1)
        storage.setItem(key(account), JSON.stringify(journal))
      }
    },
  }
}

export const LIBRARY_KEY = 'gtar_library_v1'
export function persistLibrary(library: SyncLibrary, storage: Storage = localStorage) {
  storage.setItem(LIBRARY_KEY, JSON.stringify(library))
}
export function readPersistedLibrary(storage: Storage = localStorage): SyncLibrary | null {
  const raw = storage.getItem(LIBRARY_KEY)
  if (!raw) return null
  const library = JSON.parse(raw) as SyncLibrary
  if (!Array.isArray(library.songs) || !Array.isArray(library.setlists)) throw new Error('Device library is damaged. Export recovery data before restoring.')
  return library
}
export function readRecoverySnapshots(account: string, storage: Storage = localStorage) {
  const snapshots: Record<string, unknown> = {}
  for (let i = 0; i < storage.length; i++) {
    const name = storage.key(i)
    if (name?.startsWith(`gtar_sync_recovery:${account}:`)) snapshots[name] = storage.getItem(name)
  }
  return snapshots
}