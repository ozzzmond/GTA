import React, { useState } from 'react'
import { Globe, ExternalLink, Plus, X, Music, Check } from 'lucide-react'

interface WebsiteUrlSourceModalProps {
  isOpen: boolean
  onClose: () => void
  onImportFromUrl?: (url: string) => void
}

interface ExternalSource {
  name: string
  url: string
  desc: string
}

const DEFAULT_SOURCES: ExternalSource[] = [
  {
    name: 'songselect.ccli.com',
    url: 'https://songselect.ccli.com',
    desc: 'Praise & Worship / Church Chords',
  },
  {
    name: 'ultimate-guitar.com',
    url: 'https://www.ultimate-guitar.com',
    desc: 'Global chords & tabs catalog',
  },
  {
    name: 'chordie.com',
    url: 'https://www.chordie.com',
    desc: 'Direct ChordPro catalog',
  },
  {
    name: 'opmtunes.com',
    url: 'https://www.opmtunes.com',
    desc: 'OPM hits & Pinoy classics',
  },
]

export const WebsiteUrlSourceModal: React.FC<WebsiteUrlSourceModalProps> = ({
  isOpen,
  onClose,
  onImportFromUrl,
}) => {
  const [sources, setSources] = useState<ExternalSource[]>(DEFAULT_SOURCES)
  const [showAddForm, setShowAddForm] = useState(false)
  const [customName, setCustomName] = useState('')
  const [customUrl, setCustomUrl] = useState('')
  const [customDesc, setCustomDesc] = useState('')
  const [directUrl, setDirectUrl] = useState('')
  const [toastMessage, setToastMessage] = useState<string | null>(null)

  if (!isOpen) return null

  const handleAddSource = (e: React.FormEvent) => {
    e.preventDefault()
    if (!customUrl.trim()) return

    let formattedUrl = customUrl.trim()
    if (!/^https?:\/\//i.test(formattedUrl)) {
      formattedUrl = `https://${formattedUrl}`
    }

    const newSource: ExternalSource = {
      name: customName.trim() || new URL(formattedUrl).hostname,
      url: formattedUrl,
      desc: customDesc.trim() || 'Custom chord provider',
    }

    setSources((prev) => [...prev, newSource])
    setCustomName('')
    setCustomUrl('')
    setCustomDesc('')
    setShowAddForm(false)
    showToast('Custom website source added!')
  }

  const showToast = (msg: string) => {
    setToastMessage(msg)
    setTimeout(() => setToastMessage(null), 3000)
  }

  const handleDirectFetch = (e: React.FormEvent) => {
    e.preventDefault()
    if (!directUrl.trim()) return
    onImportFromUrl?.(directUrl.trim())
    showToast('Opening song link in new tab...')
    window.open(directUrl.trim(), '_blank', 'noopener,noreferrer')
  }

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/75 backdrop-blur-sm animate-fade-in">
      <div className="w-full max-w-lg rounded-2xl bg-[#073642] border border-[#1A4A55] shadow-2xl overflow-hidden flex flex-col max-h-[90vh]">
        {/* Header */}
        <div className="px-6 py-4 border-b border-[#1A4A55] flex items-center justify-between bg-[#002B36]/50">
          <div className="flex items-center gap-3">
            <div className="w-9 h-9 rounded-xl bg-[#2AA198]/20 border border-[#2AA198]/30 flex items-center justify-center text-[#2AA198]">
              <Globe className="w-5 h-5" />
            </div>
            <div>
              <h2 className="text-base font-bold text-[#FDF6E3]">Browse Song Sources</h2>
              <p className="text-xs text-[#93A1A1]">
                Open chords online with 1-tap song chords import
              </p>
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

        {/* Content */}
        <div className="p-6 overflow-y-auto space-y-4 flex-1">
          {toastMessage && (
            <div className="px-3.5 py-2 rounded-xl bg-[#2AA198]/15 border border-[#2AA198]/40 text-[#2AA198] text-xs font-semibold flex items-center gap-2">
              <Check className="w-4 h-4" />
              <span>{toastMessage}</span>
            </div>
          )}

          {/* Quick URL Input */}
          <form onSubmit={handleDirectFetch} className="space-y-2">
            <label className="text-xs font-bold text-[#EEE8D5] uppercase tracking-wide">
              Direct Chord Sheet URL
            </label>
            <div className="flex gap-2">
              <input
                type="url"
                value={directUrl}
                onChange={(e) => setDirectUrl(e.target.value)}
                placeholder="https://ultimate-guitar.com/tabs/..."
                className="flex-1 bg-[#002B36] border border-[#1A4A55] rounded-xl px-3.5 py-2 text-xs text-[#FDF6E3] placeholder-[#93A1A1]/60 focus:outline-none focus:border-[#2AA198]"
              />
              <button
                type="submit"
                className="px-4 py-2 bg-[#2AA198] text-[#002B36] font-bold rounded-xl text-xs hover:bg-[#35B8AD] transition-colors cursor-pointer flex items-center gap-1.5"
              >
                <ExternalLink className="w-3.5 h-3.5" />
                <span>Open</span>
              </button>
            </div>
          </form>

          {/* Sources List */}
          <div className="space-y-2 pt-2">
            <span className="text-[11px] font-bold uppercase tracking-wider text-[#B58900]">
              Verified Chord Repositories
            </span>

            <div className="space-y-2">
              {sources.map((src, idx) => (
                <a
                  key={idx}
                  href={src.url}
                  target="_blank"
                  rel="noopener noreferrer"
                  className="flex items-center justify-between p-3.5 rounded-xl bg-[#002B36] border border-[#1A4A55] hover:border-[#2AA198] transition-all group cursor-pointer"
                >
                  <div className="flex items-center gap-3">
                    <div className="w-8 h-8 rounded-lg bg-[#073642] flex items-center justify-center text-[#2AA198]">
                      <Music className="w-4 h-4" />
                    </div>
                    <div>
                      <div className="text-xs font-bold text-[#FDF6E3] group-hover:text-[#2AA198] transition-colors">
                        {src.name}
                      </div>
                      <div className="text-[11px] text-[#93A1A1]">{src.desc}</div>
                    </div>
                  </div>
                  <ExternalLink className="w-4 h-4 text-[#93A1A1] group-hover:text-[#2AA198] transition-colors" />
                </a>
              ))}
            </div>
          </div>

          {/* Add Custom Website Form */}
          {showAddForm ? (
            <form
              onSubmit={handleAddSource}
              className="p-4 rounded-xl bg-[#002B36] border border-[#1A4A55] space-y-3"
            >
              <div className="text-xs font-bold text-[#2AA198]">Add Custom Website URL</div>
              <input
                type="text"
                value={customName}
                onChange={(e) => setCustomName(e.target.value)}
                placeholder="Source Name (e.g. My Praise Chords)"
                className="w-full bg-[#073642] border border-[#1A4A55] rounded-lg px-3 py-1.5 text-xs text-[#FDF6E3] placeholder-[#93A1A1]/60 focus:outline-none focus:border-[#2AA198]"
              />
              <input
                type="url"
                required
                value={customUrl}
                onChange={(e) => setCustomUrl(e.target.value)}
                placeholder="Website URL (https://...)"
                className="w-full bg-[#073642] border border-[#1A4A55] rounded-lg px-3 py-1.5 text-xs text-[#FDF6E3] placeholder-[#93A1A1]/60 focus:outline-none focus:border-[#2AA198]"
              />
              <input
                type="text"
                value={customDesc}
                onChange={(e) => setCustomDesc(e.target.value)}
                placeholder="Description (e.g. Church setlist charts)"
                className="w-full bg-[#073642] border border-[#1A4A55] rounded-lg px-3 py-1.5 text-xs text-[#FDF6E3] placeholder-[#93A1A1]/60 focus:outline-none focus:border-[#2AA198]"
              />
              <div className="flex justify-end gap-2 pt-1">
                <button
                  type="button"
                  onClick={() => setShowAddForm(false)}
                  className="px-3 py-1.5 text-xs text-[#93A1A1] hover:text-[#FDF6E3] cursor-pointer"
                >
                  Cancel
                </button>
                <button
                  type="submit"
                  className="px-3 py-1.5 bg-[#2AA198] text-[#002B36] font-bold rounded-lg text-xs hover:bg-[#35B8AD] cursor-pointer"
                >
                  Save Source
                </button>
              </div>
            </form>
          ) : (
            <button
              type="button"
              onClick={() => setShowAddForm(true)}
              className="w-full py-2.5 rounded-xl border border-dashed border-[#2AA198]/50 text-[#2AA198] text-xs font-bold hover:bg-[#2AA198]/10 transition-colors cursor-pointer flex items-center justify-center gap-2"
            >
              <Plus className="w-4 h-4" />
              <span>+ Add Custom Website URL</span>
            </button>
          )}
        </div>

        {/* Footer */}
        <div className="px-6 py-3 border-t border-[#1A4A55] bg-[#002B36]/30 flex justify-end">
          <button
            type="button"
            onClick={onClose}
            className="px-4 py-1.5 rounded-xl bg-[#002B36] border border-[#1A4A55] text-xs font-semibold text-[#93A1A1] hover:text-[#FDF6E3] transition-colors cursor-pointer"
          >
            Close
          </button>
        </div>
      </div>
    </div>
  )
}
