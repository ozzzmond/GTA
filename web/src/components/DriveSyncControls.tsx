import type { useDriveSync } from '../hooks/useDriveSync'
export function DriveSyncControls({ sync }: { sync: ReturnType<typeof useDriveSync> }) {
  return <div className="flex flex-wrap items-center gap-2 px-4 py-2 bg-[#073642] border-b border-[#1A4A55] text-xs">
    {sync.session?.user.picture && <img src={sync.session.user.picture} alt="" referrerPolicy="no-referrer" className="w-6 h-6 rounded-full" />}
    <span>{sync.session?.user.email ?? 'Google Drive backup'}</span>
    {sync.session ? <>
      <button className="px-3 py-1 border rounded" onClick={sync.signOut}>Sign Out</button>
      <button className="px-3 py-1 border rounded disabled:opacity-50" disabled={sync.busy} onClick={() => void sync.syncNow()}>Sync Now</button>
    </> : <button className="px-3 py-1 border rounded disabled:opacity-50" disabled={!sync.ready || sync.busy} onClick={() => void sync.signIn()}>Sign In with Google</button>}
    <span role="status" className="text-[#93A1A1]">{sync.status}</span>
  </div>
}
