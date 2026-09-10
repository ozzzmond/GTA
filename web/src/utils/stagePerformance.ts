/**
 * stagePerformance.ts
 *
 * Cross-platform utilities for GTAR Stage View gig performance:
 *   1. Fullscreen — safe feature-detection wrapper with iOS / iOS-PWA awareness.
 *   2. Screen Wake Lock — keeps the screen awake while on stage, with automatic
 *      re-acquisition after tab switches and clean release on unmount.
 *
 * Both factories return plain objects (no React dependency) so they can be
 * unit-tested independently and composed easily inside useEffect hooks.
 */

// ---------------------------------------------------------------------------
// Platform detection helpers
// ---------------------------------------------------------------------------

/**
 * Returns true when running inside an iOS standalone PWA (Add to Home Screen)
 * or when the browser is iOS Safari where requestFullscreen is unsupported.
 *
 * iOS detection strategy (no user-agent string parsing):
 *   - `navigator.standalone` is `true` only in iOS standalone PWA.
 *   - `matchMedia('(display-mode: standalone)')` covers both iOS and Android PWAs,
 *     but we combine it with the touch check to target iOS specifically.
 *   - `navigator.maxTouchPoints > 1` + lack of `MSStream` (IE guard) reliably
 *     identifies iOS without sniffing the UA string.
 */
export function isIosDevice(): boolean {
  if (typeof window === 'undefined') return false
  // iPads on iOS 13+ report 'MacIntel' so we rely on touch points
  const hasTouch = navigator.maxTouchPoints > 1
  // Quick heuristic: iOS Safari sets navigator.standalone (boolean | undefined)
  const standaloneApi = 'standalone' in navigator
  return hasTouch && standaloneApi
}

/**
 * Returns true when the app is running as an installed iOS or Android PWA
 * in standalone display mode (i.e. no browser chrome / address bar).
 */
export function isStandalonePwa(): boolean {
  if (typeof window === 'undefined') return false
  // navigator.standalone is iOS-specific (true when launched from Home Screen)
  if ((navigator as any).standalone === true) return true
  // display-mode: standalone covers Android Chrome PWAs and iOS Safari PWAs
  try {
    return window.matchMedia('(display-mode: standalone)').matches
  } catch {
    return false
  }
}

/**
 * Returns true when the native Fullscreen API is available and usable.
 * iOS Safari does NOT support requestFullscreen on arbitrary elements.
 */
export function isFullscreenApiSupported(): boolean {
  if (typeof document === 'undefined') return false
  return Boolean(
    document.fullscreenEnabled ||
      (document as any).webkitFullscreenEnabled ||
      (document as any).mozFullScreenEnabled ||
      (document as any).msFullscreenEnabled
  )
}

// ---------------------------------------------------------------------------
// 1. Fullscreen Controller
// ---------------------------------------------------------------------------

export interface FullscreenController {
  /**
   * Toggle between fullscreen and windowed mode.
   * On iOS where the API is unavailable, this is a no-op (use the `isSupported`
   * flag to show a platform-appropriate fallback instead).
   */
  toggle(): void
  /** True when the native Fullscreen API is available on this browser. */
  readonly isSupported: boolean
  /**
   * Register a callback that fires whenever the fullscreen state changes
   * (covers both programmatic toggling and the user pressing Escape).
   * Returns an unsubscribe function.
   */
  onChange(callback: (isFullscreen: boolean) => void): () => void
  /** Remove all event listeners added by this controller. */
  cleanup(): void
}

export function createFullscreenController(): FullscreenController {
  const supported = isFullscreenApiSupported()
  const listeners: Array<(isFullscreen: boolean) => void> = []

  function getCurrentState(): boolean {
    return Boolean(
      document.fullscreenElement ||
        (document as any).webkitFullscreenElement ||
        (document as any).mozFullScreenElement ||
        (document as any).msFullscreenElement
    )
  }

  function dispatchChange() {
    const state = getCurrentState()
    listeners.forEach((cb) => cb(state))
  }

  // Vendor-prefixed event names
  const EVENTS = [
    'fullscreenchange',
    'webkitfullscreenchange',
    'mozfullscreenchange',
    'MSFullscreenChange',
  ]

  if (supported) {
    EVENTS.forEach((evt) => document.addEventListener(evt, dispatchChange))
  }

  const controller: FullscreenController = {
    get isSupported() {
      return supported
    },

    toggle() {
      if (!supported) return
      try {
        if (getCurrentState()) {
          // Exit fullscreen
          if (document.exitFullscreen) {
            document.exitFullscreen().catch(() => {})
          } else if ((document as any).webkitExitFullscreen) {
            ;(document as any).webkitExitFullscreen()
          } else if ((document as any).mozCancelFullScreen) {
            ;(document as any).mozCancelFullScreen()
          } else if ((document as any).msExitFullscreen) {
            ;(document as any).msExitFullscreen()
          }
        } else {
          // Enter fullscreen on the root element
          const el = document.documentElement
          if (el.requestFullscreen) {
            el.requestFullscreen().catch(() => {})
          } else if ((el as any).webkitRequestFullscreen) {
            ;(el as any).webkitRequestFullscreen()
          } else if ((el as any).mozRequestFullScreen) {
            ;(el as any).mozRequestFullScreen()
          } else if ((el as any).msRequestFullscreen) {
            ;(el as any).msRequestFullscreen()
          }
        }
      } catch {
        // Ignore any synchronous errors (e.g. NotAllowedError in some browsers
        // when called outside a user gesture — shouldn't happen but guard anyway)
      }
    },

    onChange(callback) {
      listeners.push(callback)
      return () => {
        const idx = listeners.indexOf(callback)
        if (idx !== -1) listeners.splice(idx, 1)
      }
    },

    cleanup() {
      if (supported) {
        EVENTS.forEach((evt) => document.removeEventListener(evt, dispatchChange))
      }
      listeners.length = 0
    },
  }

  return controller
}

// ---------------------------------------------------------------------------
// 2. Screen Wake Lock Controller
// ---------------------------------------------------------------------------

export interface WakeLockController {
  /**
   * Request a screen wake lock. Safe to call if already held or unsupported.
   * Returns a promise that resolves when the lock is acquired (or immediately
   * if unsupported, so callers can always await it).
   */
  acquire(): Promise<void>
  /** Release the current wake lock sentinel if held. */
  release(): Promise<void>
  /** Release the lock and remove all internal listeners. */
  cleanup(): Promise<void>
  /** Whether the Wake Lock API is available in this browser. */
  readonly isSupported: boolean
}

export function createWakeLockController(): WakeLockController {
  const supported = typeof navigator !== 'undefined' && 'wakeLock' in navigator

  let sentinel: WakeLockSentinel | null = null
  let visibilityHandler: (() => void) | null = null
  let destroyed = false

  async function acquireInternal(): Promise<void> {
    if (!supported || destroyed) return
    // If we already hold an active sentinel, nothing to do
    if (sentinel && !sentinel.released) return
    try {
      sentinel = await (navigator as any).wakeLock.request('screen')
    } catch {
      // NotAllowedError when the page is not visible, or the browser denied it.
      // This is expected and safe to ignore.
      sentinel = null
    }
  }

  async function releaseInternal(): Promise<void> {
    if (sentinel && !sentinel.released) {
      try {
        await sentinel.release()
      } catch {
        // Ignore errors during release (e.g. already released by browser)
      }
    }
    sentinel = null
  }

  // Re-acquire when the tab becomes visible again (handles Alt+Tab / home button)
  function setupVisibilityListener() {
    if (!supported) return
    visibilityHandler = () => {
      if (document.visibilityState === 'visible' && !destroyed) {
        acquireInternal()
      }
    }
    document.addEventListener('visibilitychange', visibilityHandler)
  }

  function teardownVisibilityListener() {
    if (visibilityHandler) {
      document.removeEventListener('visibilitychange', visibilityHandler)
      visibilityHandler = null
    }
  }

  // Set up the re-acquisition listener eagerly so it's ready before acquire()
  setupVisibilityListener()

  const controller: WakeLockController = {
    get isSupported() {
      return supported
    },

    async acquire() {
      await acquireInternal()
    },

    async release() {
      await releaseInternal()
    },

    async cleanup() {
      destroyed = true
      teardownVisibilityListener()
      await releaseInternal()
    },
  }

  return controller
}
