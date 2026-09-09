import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import { registerSW } from 'virtual:pwa-register'
import './index.css'
import App from './App.tsx'

import { appLogger } from './utils/logger'

// Initialize the in-browser logging engine
appLogger.init()

// Apply debug environment branding if active
if (import.meta.env.DEV) {
  if (typeof document !== 'undefined') {
    const isPresentation =
      window.location.pathname.includes('/stage/present') ||
      window.location.search.includes('view=present') ||
      window.location.hash.includes('present')
    document.title = isPresentation ? 'GTAR Stage Display' : 'GTAR-Dev Live Stage Companion'
    try {
      const devFaviconSvg = `<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 100 100">
  <defs>
    <linearGradient id="redGrad" x1="0" y1="0" x2="1" y2="1">
      <stop offset="0%" stop-color="#E50914" />
      <stop offset="100%" stop-color="#8B0000" />
    </linearGradient>
  </defs>
  <rect width="100" height="100" rx="24" fill="url(#redGrad)" stroke="#FFFFFF" stroke-width="3"/>
  <circle cx="50" cy="42" r="22" fill="#002B36" stroke="#FFFFFF" stroke-width="2"/>
  <path d="M42 32 L62 42 L42 52 Z" fill="#2AA198" />
  <rect x="8" y="66" width="84" height="26" rx="6" fill="#FF1744" stroke="#FFFFFF" stroke-width="2.5"/>
  <text x="50" y="84" font-family="system-ui, -apple-system, sans-serif" font-weight="900" font-size="16" fill="#FFFFFF" text-anchor="middle" letter-spacing="2">DEV</text>
</svg>`
      const iconUrl = `data:image/svg+xml;base64,${btoa(devFaviconSvg)}`
      let link = document.querySelector("link[rel*='icon']") as HTMLLinkElement
      if (!link) {
        link = document.createElement('link')
        link.rel = 'icon'
        document.head.appendChild(link)
      }
      link.type = 'image/svg+xml'
      link.href = iconUrl
    } catch { }
  }
} else {
  // Production: explicitly ensure favicon points to the GTAR teal guitar icon
  if (typeof document !== 'undefined') {
    try {
      let link = document.querySelector("link[rel*='icon']") as HTMLLinkElement
      if (!link) {
        link = document.createElement('link')
        link.rel = 'icon'
        document.head.appendChild(link)
      }
      link.type = 'image/svg+xml'
      link.href = '/favicon.svg'
    } catch { }
  }
}

// Register PWA Service Worker for offline stage caching & local testing
if (typeof window !== 'undefined' && 'serviceWorker' in navigator) {
  registerSW({
    immediate: true,
    onRegisteredSW(_swScriptUrl, registration) {
      if (registration) {
        // Periodically check for updates (every hour)
        setInterval(() => {
          registration.update().catch(() => { })
        }, 60 * 60 * 1000)
      }
    },
    onRegisterError(error) {
      console.warn('GTAR Service Worker registration error:', error)
    },
  })
}

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <App />
  </StrictMode>,
)
