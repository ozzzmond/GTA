import React from 'react'
import { X, Check, Palette, Sparkles } from 'lucide-react'

export type ThemeMode = 'solarized-dark' | 'amber-stage' | 'oled-black' | 'paper-light'

export interface ThemeOption {
  id: ThemeMode
  name: string
  subtitle: string
  bgHex: string
  surfaceHex: string
  accentHex: string
  textHex: string
  tag?: string
}

export const THEME_OPTIONS: ThemeOption[] = [
  {
    id: 'solarized-dark',
    name: 'Solarized Dark',
    subtitle: 'Classic GTAR stage theme with rich teal & cyan contrasts',
    bgHex: '#002B36',
    surfaceHex: '#073642',
    accentHex: '#2AA198',
    textHex: '#EEE8D5',
    tag: 'DEFAULT',
  },
  {
    id: 'amber-stage',
    name: 'Amber Stage',
    subtitle: 'Warm high-visibility amber & gold chords for live gigs',
    bgHex: '#0d131a',
    surfaceHex: '#151e28',
    accentHex: '#B58900',
    textHex: '#FDF6E3',
    tag: 'POPULAR',
  },
  {
    id: 'oled-black',
    name: 'OLED Pure Black',
    subtitle: 'Ultra-high contrast deep black for battery conservation and zero backlight bleed',
    bgHex: '#000000',
    surfaceHex: '#111111',
    accentHex: '#10B981',
    textHex: '#FFFFFF',
    tag: 'BATTERY',
  },
  {
    id: 'paper-light',
    name: 'Paper Cream Light',
    subtitle: 'Warm soft paper with high-contrast dark text & burnt amber chords',
    bgHex: '#f4ecd8',
    surfaceHex: '#ebe0c7',
    accentHex: '#b45309',
    textHex: '#111827',
    tag: 'DAYLIGHT',
  },
]

interface ThemeModalProps {
  isOpen: boolean
  onClose: () => void
  currentTheme: ThemeMode
  onSelectTheme: (theme: ThemeMode) => void
}

export const ThemeModal: React.FC<ThemeModalProps> = ({
  isOpen,
  onClose,
  currentTheme,
  onSelectTheme,
}) => {
  if (!isOpen) return null

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/75 backdrop-blur-sm animate-fade-in select-none">
      <div className="w-full max-w-md rounded-2xl bg-[#073642] border border-[#1A4A55] shadow-2xl overflow-hidden flex flex-col max-h-[90vh]">
        {/* Modal Header */}
        <div className="px-5 py-4 border-b border-[#1A4A55] flex items-center justify-between bg-[#002B36]/60">
          <div className="flex items-center gap-2.5">
            <div className="w-9 h-9 rounded-xl bg-[#B58900]/20 border border-[#B58900]/40 flex items-center justify-center text-[#B58900]">
              <Palette className="w-5 h-5" />
            </div>
            <div>
              <h2 className="text-sm sm:text-base font-bold text-[#FDF6E3]">Stage Color Theme</h2>
              <p className="text-[11px] text-[#93A1A1]">Live Performance & Teleprompter Palette</p>
            </div>
          </div>
          <button
            type="button"
            onClick={onClose}
            className="p-1.5 rounded-lg text-[#93A1A1] hover:text-[#FDF6E3] hover:bg-[#002B36] transition-colors cursor-pointer"
          >
            <X className="w-4 h-4" />
          </button>
        </div>

        {/* Modal Body */}
        <div className="p-5 overflow-y-auto space-y-3 flex-1">
          {THEME_OPTIONS.map((theme) => {
            const isSelected = currentTheme === theme.id
            return (
              <div
                key={theme.id}
                onClick={() => {
                  onSelectTheme(theme.id)
                  onClose()
                }}
                className={`p-3.5 rounded-2xl border transition-all cursor-pointer flex items-center justify-between gap-3 group ${
                  isSelected
                    ? 'bg-[#002B36] border-[#2AA198] shadow-md ring-1 ring-[#2AA198]/40'
                    : 'bg-[#002B36]/50 border-[#1A4A55]/60 hover:border-[#2AA198]/50 hover:bg-[#002B36]'
                }`}
              >
                <div className="flex items-center gap-3 min-w-0">
                  {/* Swatch Preview Box */}
                  <div
                    className="w-10 h-10 rounded-xl flex items-center justify-center border shrink-0 shadow-inner relative overflow-hidden"
                    style={{
                      backgroundColor: theme.bgHex,
                      borderColor: theme.accentHex,
                    }}
                  >
                    <div
                      className="w-4 h-4 rounded-full"
                      style={{ backgroundColor: theme.accentHex }}
                    />
                  </div>

                  <div className="min-w-0">
                    <div className="flex items-center gap-2">
                      <span
                        className={`text-xs font-bold leading-tight ${
                          isSelected ? 'text-[#FDF6E3]' : 'text-[#EEE8D5] group-hover:text-[#FDF6E3]'
                        }`}
                      >
                        {theme.name}
                      </span>
                      {theme.tag && (
                        <span className="text-[9px] font-mono font-bold px-1.5 py-0.2 rounded bg-[#073642] text-[#2AA198] border border-[#1A4A55]">
                          {theme.tag}
                        </span>
                      )}
                    </div>
                    <p className="text-[11px] text-[#93A1A1] mt-0.5 line-clamp-1">
                      {theme.subtitle}
                    </p>
                  </div>
                </div>

                <div
                  className={`w-5 h-5 rounded-full border flex items-center justify-center shrink-0 transition-colors ${
                    isSelected
                      ? 'border-[#2AA198] bg-[#2AA198] text-[#002B36]'
                      : 'border-[#1A4A55] bg-[#073642] group-hover:border-[#2AA198]'
                  }`}
                >
                  {isSelected && <Check className="w-3.5 h-3.5 stroke-[3]" />}
                </div>
              </div>
            )
          })}
        </div>

        {/* Modal Footer Note */}
        <div className="px-5 py-3 border-t border-[#1A4A55] bg-[#002B36]/40 flex items-center justify-between text-[11px] font-mono text-[#93A1A1]">
          <span className="flex items-center gap-1.5">
            <Sparkles className="w-3.5 h-3.5 text-[#B58900]" />
            Saved directly to LocalStorage
          </span>
          <button
            type="button"
            onClick={onClose}
            className="px-3 py-1 rounded-lg bg-[#073642] hover:bg-[#1A4A55] text-[#FDF6E3] font-bold text-xs cursor-pointer transition-colors"
          >
            Done
          </button>
        </div>
      </div>
    </div>
  )
}
