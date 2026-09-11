import { parseBackupJson, type FullBackupPayload, type ParsedBackupResult } from './jsonBackup'

const API = 'https://www.googleapis.com/drive/v3/files'
const NAME = 'gtar_songbook_sync.json'
const FIELDS = 'id,name,version,modifiedTime,md5Checksum'
export class DriveSyncError extends Error {
  status: number
  constructor(message: string, status = 0) { super(message); this.status = status }
}
export interface SyncFile { id: string; name?: string; version?: string; modifiedTime?: string; md5Checksum?: string }
function revision(file: SyncFile): string {
  for (const key of ['version', 'modifiedTime', 'md5Checksum'] as const) {
    if (typeof file[key] === 'string' && file[key]) return `${key}:${file[key]}`
  }
  throw new DriveSyncError('Drive metadata has no revision. Try Sync Now again.')
}
function sameFile(a: SyncFile | null, b: SyncFile | null): boolean {
  return a && b ? a.id === b.id && revision(a) === revision(b) : a === b
}
async function getMetadata(token: string, id: string): Promise<SyncFile> {
  return (await request(token, `${API}/${encodeURIComponent(id)}?${new URLSearchParams({ fields: FIELDS })}`)).json()
}
interface Snapshot { file: SyncFile | null; etag?: string }
const snapshots = new Map<string, Snapshot>()
export function clearDriveSession(token: string) { snapshots.delete(token) }
async function request(token: string, url: string, init: RequestInit = {}) {
  const response = await fetch(url, { ...init, headers: { Authorization: `Bearer ${token}`, ...init.headers }, signal: AbortSignal.timeout(30000) })
  if (!response.ok) throw new DriveSyncError(response.status === 401 ? 'Google session expired. Sign in again.' : response.status === 412 ? 'Cloud changed during sync. Try Sync Now again.' : `Drive request failed (${response.status}). Local changes are saved.`, response.status)
  return response
}
export async function findSyncFile(token: string): Promise<SyncFile | null> {
  const query = new URLSearchParams({ spaces: 'appDataFolder', q: `name = '${NAME}' and trashed = false`, fields: `files(${FIELDS}),nextPageToken`, pageSize: '100' })
  const data = await (await request(token, `${API}?${query}`)).json()
  if (!Array.isArray(data.files) || data.nextPageToken || data.files.length > 1) throw new DriveSyncError('Ambiguous cloud backup. Sync stopped to protect your data.')
  return data.files[0] ?? null
}
export async function pullCloudBackup(token: string): Promise<ParsedBackupResult | null> {
  snapshots.delete(token)
  const file = await findSyncFile(token)
  if (!file) { snapshots.set(token, { file: null }); return null }
  const response = await request(token, `${API}/${encodeURIComponent(file.id)}?alt=media`)
  const parsed = parseBackupJson(await response.text())
  if (!parsed.isValid || parsed.isSingleSetlist) throw new DriveSyncError(parsed.error ?? 'Expected a full library backup.')
  const after = await getMetadata(token, file.id)
  if (!sameFile(after, file)) throw new DriveSyncError('Cloud changed while downloading. Try Sync Now again.')
  snapshots.set(token, { file: after, etag: response.headers.get('etag') ?? undefined })
  return parsed
}
export async function pushCloudBackup(token: string, payload: FullBackupPayload): Promise<void> {
  const parsed = parseBackupJson(JSON.stringify(payload))
  if (!parsed.isValid || parsed.isSingleSetlist) throw new DriveSyncError(parsed.error ?? 'Invalid backup')
  const snapshot = snapshots.get(token)
  if (!snapshot) throw new DriveSyncError('Download and validate the cloud backup before uploading.')
  const current = await findSyncFile(token)
  if (!sameFile(current, snapshot.file) || (current && !sameFile(await getMetadata(token, current.id), snapshot.file))) throw new DriveSyncError('Cloud changed since download. Try Sync Now again.')
  const boundary = `gtar_${crypto.randomUUID()}`
  const metadata = current ? { name: NAME } : { name: NAME, parents: ['appDataFolder'] }
  const body = `--${boundary}\r\nContent-Type: application/json; charset=UTF-8\r\n\r\n${JSON.stringify(metadata)}\r\n--${boundary}\r\nContent-Type: application/json\r\n\r\n${JSON.stringify(payload)}\r\n--${boundary}--`
  // Consume the guard even on failure: an uncertain upload must be pulled again.
  snapshots.delete(token)
  const response = await request(token, `https://www.googleapis.com/upload/drive/v3/files${current ? '/' + encodeURIComponent(current.id) : ''}?${new URLSearchParams({ uploadType: 'multipart', fields: FIELDS })}`, {
    method: current ? 'PATCH' : 'POST', body,
    headers: { 'Content-Type': `multipart/related; boundary=${boundary}`, ...(snapshot.etag ? { 'If-Match': snapshot.etag } : {}) },
  })
  const uploaded: SyncFile = await response.json()
  if (!uploaded.id || (current && uploaded.id !== current.id)) throw new DriveSyncError('Unexpected upload metadata. Pull again before uploading.')
  revision(uploaded)
  snapshots.set(token, { file: uploaded, etag: response.headers.get('etag') ?? undefined })
}

