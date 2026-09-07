import React, { useState } from 'react'
import { Lock, Guitar, Eye, EyeOff, ArrowRight } from 'lucide-react'

interface LoginWallProps {
  onUnlock: () => void
}

const DEFAULT_PIN = 'gtar2026'

export const LoginWall: React.FC<LoginWallProps> = ({ onUnlock }) => {
  const [pin, setPin] = useState('')
  const [showPin, setShowPin] = useState(false)
  const [isShaking, setIsShaking] = useState(false)
  const [errorMessage, setErrorMessage] = useState<string | null>(null)

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault()

    if (pin === DEFAULT_PIN) {
      sessionStorage.setItem('gtar_authenticated', 'true')
      onUnlock()
    } else {
      setErrorMessage('Invalid Stage Passcode')
      setIsShaking(true)
      setTimeout(() => setIsShaking(false), 500)
    }
  }

  const handleQuickUnlock = () => {
    setPin(DEFAULT_PIN)
    sessionStorage.setItem('gtar_authenticated', 'true')
    onUnlock()
  }

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-[#002B36] p-4 select-none">
      {/* Background ambient stage glow */}
      <div className="absolute top-1/4 -left-20 w-96 h-96 rounded-full bg-[#2AA198]/10 blur-3xl pointer-events-none" />
      <div className="absolute bottom-1/4 -right-20 w-96 h-96 rounded-full bg-[#CB4B16]/10 blur-3xl pointer-events-none" />

      <div
        className={`w-full max-w-md rounded-2xl border border-[#1A4A55] bg-[#073642] p-8 shadow-2xl shadow-black/60 transition-all ${
          isShaking ? 'animate-shake' : ''
        }`}
      >
        {/* GTAR Stage Branding Header */}
        <div className="flex flex-col items-center text-center mb-6">
          <div className="w-16 h-16 rounded-2xl bg-gradient-to-br from-[#2AA198] to-[#073642] p-0.5 mb-4 shadow-lg shadow-[#2AA198]/20 flex items-center justify-center">
            <div className="w-full h-full bg-[#002B36] rounded-[14px] flex items-center justify-center text-[#2AA198]">
              <Guitar className="w-8 h-8" />
            </div>
          </div>
          <h1 className="text-2xl font-bold tracking-tight text-[#FDF6E3] flex items-center gap-2">
            GTAR <span className="text-[#2AA198] text-sm px-2 py-0.5 rounded-full border border-[#2AA198]/30 bg-[#2AA198]/10 font-mono">Stage Companion</span>
          </h1>
          <p className="text-xs text-[#93A1A1] mt-1.5 font-mono">
            Desktop Workspace & 1:1 Stage Parity
          </p>
        </div>

        {/* Form */}
        <form onSubmit={handleSubmit} className="space-y-4">
          <div>
            <label className="block text-xs font-semibold uppercase tracking-wider text-[#93A1A1] mb-2 font-mono">
              Stage Passcode / PIN
            </label>
            <div className="relative">
              <div className="absolute inset-y-0 left-0 pl-3.5 flex items-center pointer-events-none text-[#93A1A1]">
                <Lock className="w-4 h-4" />
              </div>
              <input
                type={showPin ? 'text' : 'password'}
                value={pin}
                onChange={(e) => {
                  setPin(e.target.value)
                  if (errorMessage) setErrorMessage(null)
                }}
                placeholder="Enter passcode (e.g. gtar2026)"
                autoFocus
                className="w-full pl-10 pr-10 py-3 rounded-xl bg-[#002B36] border border-[#1A4A55] text-[#FDF6E3] placeholder-[#93A1A1]/50 text-sm font-mono focus:outline-none focus:border-[#2AA198] focus:ring-1 focus:ring-[#2AA198] transition-all"
              />
              <button
                type="button"
                onClick={() => setShowPin(!showPin)}
                className="absolute inset-y-0 right-0 pr-3.5 flex items-center text-[#93A1A1] hover:text-[#FDF6E3] transition-colors"
                tabIndex={-1}
              >
                {showPin ? <EyeOff className="w-4 h-4" /> : <Eye className="w-4 h-4" />}
              </button>
            </div>
          </div>

          {errorMessage && (
            <p className="text-xs text-[#DC6E67] font-mono flex items-center gap-1.5 animate-pulse">
              <span>⚠️</span> {errorMessage}
            </p>
          )}

          <button
            type="submit"
            className="w-full py-3 px-4 rounded-xl bg-gradient-to-r from-[#2AA198] to-[#35B8AD] hover:from-[#35B8AD] hover:to-[#2AA198] text-[#002B36] font-bold text-sm tracking-wide shadow-md shadow-[#2AA198]/20 flex items-center justify-center gap-2 transition-all active:scale-[0.99] cursor-pointer"
          >
            <span>Unlock Workspace</span>
            <ArrowRight className="w-4 h-4" />
          </button>
        </form>

        {/* Quick hint & default unlock helper */}
        <div className="mt-6 pt-5 border-t border-[#1A4A55]/60 flex items-center justify-between text-xs text-[#93A1A1]">
          <span className="font-mono">Default: <code className="text-[#B58900]">gtar2026</code></span>
          <button
            type="button"
            onClick={handleQuickUnlock}
            className="text-[#2AA198] hover:underline cursor-pointer font-medium"
          >
            Quick Fill & Enter
          </button>
        </div>
      </div>
    </div>
  )
}
