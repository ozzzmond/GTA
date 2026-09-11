export function authorizedEmail(email: string, configured?: string): boolean {
  const allowed = (configured ?? 'jlopez3rd@gmail.com').split(/[,;\s]+/).map(value => value.trim().toLowerCase()).filter(Boolean)
  return allowed.includes(email.trim().toLowerCase())
}
export function allowLocalBypass(dev: boolean, hostname: string): boolean {
  return dev && ['localhost', '127.0.0.1', '[::1]', '::1'].includes(hostname)
}
