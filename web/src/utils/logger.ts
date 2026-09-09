/**
 * Web Logging Engine for GTAR
 * Intercepts console errors, warnings, unhandled window exceptions and promise rejections.
 * Stores timestamped logs in localStorage and provides human-readable export for Notepad.
 */

export type LogLevel = 'DEBUG' | 'INFO' | 'WARN' | 'ERROR'

export interface LogEntry {
  id: string
  timestamp: string
  level: LogLevel
  tag: string
  message: string
  stack?: string
  details?: string
}

const STORAGE_KEY = 'gtar_web_debug_logs'
const MAX_LOGS = 1000

type LogListener = (logs: LogEntry[]) => void

class WebLoggerEngine {
  private logs: LogEntry[] = []
  private listeners: Set<LogListener> = new Set()
  private isInitialized = false
  private originalConsole = {
    error: console.error,
    warn: console.warn,
    info: console.info,
    log: console.log,
  }

  constructor() {
    this.loadPersistedLogs()
  }

  private loadPersistedLogs() {
    if (typeof window === 'undefined') return
    try {
      const raw = localStorage.getItem(STORAGE_KEY)
      if (raw) {
        const parsed = JSON.parse(raw)
        if (Array.isArray(parsed)) {
          this.logs = parsed.slice(-MAX_LOGS)
        }
      }
    } catch {
      this.logs = []
    }
  }

  private persistLogs() {
    if (typeof window === 'undefined') return
    try {
      localStorage.setItem(STORAGE_KEY, JSON.stringify(this.logs))
    } catch {
      // If quota exceeded, trim older entries
      if (this.logs.length > 100) {
        this.logs = this.logs.slice(-Math.floor(this.logs.length / 2))
        try {
          localStorage.setItem(STORAGE_KEY, JSON.stringify(this.logs))
        } catch {}
      }
    }
  }

  public init() {
    if (this.isInitialized || typeof window === 'undefined') return
    this.isInitialized = true

    // Intercept console.error
    console.error = (...args: any[]) => {
      this.originalConsole.error.apply(console, args)
      this.captureConsoleEntry('ERROR', args)
    }

    // Intercept console.warn
    console.warn = (...args: any[]) => {
      this.originalConsole.warn.apply(console, args)
      this.captureConsoleEntry('WARN', args)
    }

    // Intercept window uncaught errors
    window.addEventListener('error', (event: ErrorEvent) => {
      this.addEntry({
        level: 'ERROR',
        tag: 'WindowError',
        message: event.message || 'Uncaught window error',
        stack: event.error?.stack || `${event.filename}:${event.lineno}:${event.colno}`,
      })
    })

    // Intercept unhandled promise rejections
    window.addEventListener('unhandledrejection', (event: PromiseRejectionEvent) => {
      const reason = event.reason
      let message = 'Unhandled Promise Rejection'
      let stack: string | undefined

      if (reason instanceof Error) {
        message = reason.message
        stack = reason.stack
      } else if (typeof reason === 'string') {
        message = reason
      } else {
        try {
          message = JSON.stringify(reason)
        } catch {
          message = String(reason)
        }
      }

      this.addEntry({
        level: 'ERROR',
        tag: 'UnhandledRejection',
        message,
        stack,
      })
    })

    this.info('WebLogger', `Logger initialized. Environment: ${import.meta.env.VITE_APP_ENV || 'production'}`)
  }

  private captureConsoleEntry(level: LogLevel, args: any[]) {
    try {
      const messageParts: string[] = []
      let stack: string | undefined
      let details: string | undefined

      for (const arg of args) {
        if (arg instanceof Error) {
          messageParts.push(arg.message)
          if (!stack) stack = arg.stack
        } else if (typeof arg === 'object' && arg !== null) {
          try {
            messageParts.push(JSON.stringify(arg))
          } catch {
            messageParts.push(String(arg))
          }
        } else {
          messageParts.push(String(arg))
        }
      }

      this.addEntry({
        level,
        tag: 'Console',
        message: messageParts.join(' ') || `[Empty ${level}]`,
        stack,
        details,
      })
    } catch {
      // Safety guarantee to never throw inside console overrides
    }
  }

  public addEntry(entry: {
    level: LogLevel
    tag: string
    message: string
    stack?: string
    details?: string
  }) {
    const newLog: LogEntry = {
      id: `${Date.now()}_${Math.random().toString(36).substring(2, 7)}`,
      timestamp: new Date().toISOString(),
      ...entry,
    }

    this.logs.push(newLog)
    if (this.logs.length > MAX_LOGS) {
      this.logs = this.logs.slice(-MAX_LOGS)
    }

    this.persistLogs()
    this.notifyListeners()
  }

  public debug(tag: string, message: string, details?: string) {
    if (import.meta.env.VITE_APP_ENV !== 'debug') return
    this.addEntry({ level: 'DEBUG', tag, message, details })
  }

  public info(tag: string, message: string, details?: string) {
    this.addEntry({ level: 'INFO', tag, message, details })
  }

  public warn(tag: string, message: string, details?: string) {
    this.addEntry({ level: 'WARN', tag, message, details })
  }

  public error(tag: string, message: string, errorOrStack?: Error | string, details?: string) {
    let stack: string | undefined
    if (errorOrStack instanceof Error) {
      stack = errorOrStack.stack
    } else if (typeof errorOrStack === 'string') {
      stack = errorOrStack
    }

    this.addEntry({ level: 'ERROR', tag, message, stack, details })
  }

  public getLogs(): LogEntry[] {
    return [...this.logs]
  }

  public clearLogs() {
    this.logs = []
    if (typeof window !== 'undefined') {
      try {
        localStorage.removeItem(STORAGE_KEY)
      } catch {}
    }
    this.notifyListeners()
  }

  public subscribe(listener: LogListener): () => void {
    this.listeners.add(listener)
    listener(this.getLogs())
    return () => {
      this.listeners.delete(listener)
    }
  }

  private notifyListeners() {
    const current = this.getLogs()
    for (const listener of this.listeners) {
      try {
        listener(current)
      } catch {}
    }
  }

  public exportLogsAsText(): string {
    const header = [
      '=========================================================================',
      `GTAR WEB DEBUG LOG EXPORT - ${new Date().toISOString()}`,
      `Environment: ${import.meta.env.VITE_APP_ENV || 'production'}`,
      `User Agent: ${typeof navigator !== 'undefined' ? navigator.userAgent : 'Unknown'}`,
      `Total Log Entries: ${this.logs.length}`,
      '=========================================================================',
      '',
    ].join('\r\n')

    const body = this.logs
      .map((log) => {
        const timeStr = log.timestamp.replace('T', ' ').replace('Z', '')
        let line = `[${timeStr}] [${log.level.padEnd(5)}] [${log.tag}] ${log.message}`
        if (log.details) {
          line += `\r\n  Details: ${log.details}`
        }
        if (log.stack) {
          line += `\r\n  Stack Trace:\r\n${log.stack
            .split('\n')
            .map((s) => `    ${s.trim()}`)
            .join('\r\n')}`
        }
        return line
      })
      .join('\r\n\r\n')

    return header + body
  }

  public downloadLogFile() {
    if (typeof window === 'undefined') return
    const content = this.exportLogsAsText()
    const now = new Date()
    const yyyy = now.getFullYear()
    const mm = String(now.getMonth() + 1).padStart(2, '0')
    const dd = String(now.getDate()).padStart(2, '0')
    const hh = String(now.getHours()).padStart(2, '0')
    const min = String(now.getMinutes()).padStart(2, '0')
    const filename = `gtar-web-debug-${yyyy}-${mm}-${dd}-${hh}.${min}.log`

    const blob = new Blob([content], { type: 'text/plain;charset=utf-8' })
    const url = URL.createObjectURL(blob)
    const link = document.createElement('a')
    link.href = url
    link.download = filename
    document.body.appendChild(link)
    link.click()
    document.body.removeChild(link)
    URL.revokeObjectURL(url)
  }
}

export const appLogger = new WebLoggerEngine()
