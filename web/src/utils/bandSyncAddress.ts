export function formatLeaderAddress(raw: string): string {
  const value = raw.trim()
  if (!value) return ''
  const url = new URL(/^[a-z]+:\/\//i.test(value) ? value : `ws://${value}`)
  if (url.protocol === 'https:') url.protocol = 'wss:'
  if (url.protocol === 'http:') url.protocol = 'ws:'
  if (!['ws:', 'wss:'].includes(url.protocol) || url.username || url.password || url.hash) throw new Error('Enter a ws:// or wss:// endpoint without credentials or a fragment.')
  // Preserve the existing band-server default, while respecting explicit :80/:443
  // which URL normalizes away for ws/wss respectively.
  const authority = value.replace(/^[a-z]+:\/\//i, '').split(/[/?#]/)[0]
  const explicitPort = authority.match(/:(\d+)$/)?.[1]
  const port = url.port || explicitPort || '8765'
  return `${url.protocol}//${url.hostname}:${port}${url.pathname}${url.search}`
}

/**
 * Keeps the complete endpoint in history so reconnects retain secure schemes.
 */
export function formatLeaderDisplay(raw: string): string {
  return formatLeaderAddress(raw)
}

