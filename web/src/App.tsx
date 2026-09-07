import { useState } from 'react'
import { LoginWall } from './components/LoginWall'
import { Header } from './components/Header'
import { DesktopEditor } from './components/DesktopEditor'
import { StageView } from './components/StageView'
import { JsonBridgeModal } from './components/JsonBridgeModal'
import { KeyPickerModal } from './components/KeyPickerModal'
import { SetlistDrawer } from './components/SetlistDrawer'
import { SAMPLE_SONGS, extractDirectives } from './utils/chordSheetParser'
import type { ActiveSongState } from './types/gtar'

// Modern GTAR v1.0.40 Default Stage Setlist
const DEFAULT_SETLIST: ActiveSongState[] = [
  {
    id: 1,
    title: 'Stand By Me',
    artist: 'Ben E. King',
    key: 'A',
    capo: 'Capo 2',
    bpm: '118',
    format: 'CHORD_PRO',
    transposeOffset: 0,
    rawContent: `{title: Stand By Me}
{artist: Ben E. King}
{key: A}
{capo: Capo 2}
{tempo: 118}

Intro: [A] [F#m] [D] [E] [A]

[Verse 1]
When the [A]night has come
[F#m]And the land is dark
And the [D]moon is the [E]only light we'll [A]see
No I [A]won't be afraid, no I [F#m]won't be afraid
Just as [D]long as you [E]stand, stand by [A]me

[Chorus]
So darling, darling, [A]stand by me
Oh [F#m]stand by me
Oh [D]stand, [E]stand by me, [A]stand by me

[Verse 2]
If the [A]sky that we look upon
[F#m]Should tumble and fall
Or the [D]mountains should [E]crumble to the [A]sea
I won't [A]cry, I won't cry, no I [F#m]won't shed a tear
Just as [D]long as you [E]stand, stand by [A]me

[Outro]
[A]Whenever you're in trouble, won't you [F#m]stand by me
Oh [D]stand by me, [E]oh stand by [A]me`,
  },
  {
    id: 2,
    title: 'Ang Huling El Bimbo',
    artist: 'Eraserheads',
    key: 'G',
    capo: 'No Capo',
    bpm: '124',
    format: 'TWO_LINE',
    transposeOffset: 0,
    rawContent: `{title: Ang Huling El Bimbo}
{artist: Eraserheads}
{key: G}
{capo: No Capo}
{tempo: 124}

[Intro]
G - A7 - C - G
[G] [A7] [C] [G]

[Verse 1]
Kamukha mo si Paraluman
Nung tayo ay bata pa
At ang galing-galing mong sumayaw
Mapa-Boogie man o Cha-Cha

[Chorus]
Magkahawak ang ating kamay
At walang kamalay-malay
Na ang huling El Bimbo
Ay papunta na sa dulo

[Bridge]
At dahan-dahang lumipas
Ang mga araw at taon
Lumaki tayong dalawa
Naiwan ang kahapon

[Outro]
[G] [A7] [C] [G]
La la la la la la la la la`,
  },
  {
    id: 3,
    title: 'Hotel California',
    artist: 'Eagles',
    key: 'Bm',
    capo: 'Capo 2',
    bpm: '75',
    format: 'CHORD_PRO',
    transposeOffset: 0,
    rawContent: `{title: Hotel California}
{artist: Eagles}
{key: Bm}
{capo: Capo 2}
{tempo: 75}

Intro: [Bm] [F#7] [A] [E] [G] [D] [Em] [F#7]

[Verse 1]
On a [Bm]dark desert highway, [F#7]cool wind in my hair
[A]Warm smell of colitas [E]rising up through the air
[G]Up ahead in the distance, [D]I saw a shimmering light
[Em]My head grew heavy and my sight grew dim, [F#7]I had to stop for the night

[Chorus]
[G]Welcome to the Hotel Cali[D]fornia
Such a [F#7]lovely place, such a [Bm]lovely face
Plenty of [G]room at the Hotel Cali[D]fornia
Any [Em]time of year, you can [F#7]find it here`,
  },
  {
    id: 4,
    title: 'Hallelujah',
    artist: 'Leonard Cohen',
    key: 'C',
    capo: 'No Capo',
    bpm: '56',
    format: 'CHORD_PRO',
    transposeOffset: 0,
    rawContent: `{title: Hallelujah}
{artist: Leonard Cohen}
{key: C}
{capo: No Capo}
{tempo: 56}

Intro: [C] [Am] [C] [Am]

[Verse 1]
Now I've [C]heard there was a [Am]secret chord
That [C]David played, and it [Am]pleased the Lord
But [F]you don't really [G]care for music, [C]do you? [G]
It [C]goes like this, the [F]fourth, the [G]fifth
The [Am]minor fall, the [F]major lift
The [G]baffled king com[E7]posing Halle[Am]lujah

[Chorus]
Halle[F]lujah, Halle[Am]lujah
Halle[F]lujah, Halle[C]lu---[G]--[C]jah`,
  },
]

function App() {
  // Gated Authentication Wall (persisted in sessionStorage)
  const [isUnlocked, setIsUnlocked] = useState(() => {
    return sessionStorage.getItem('gtar_authenticated') === 'true'
  })

  // View state: Desktop Editor vs Stage View
  const [activeView, setActiveView] = useState<'editor' | 'stage'>('stage')

  // Setlist of Songs
  const [songs, setSongs] = useState<ActiveSongState[]>(DEFAULT_SETLIST)
  const [activeSongIndex, setActiveSongIndex] = useState<number>(0)

  // Current active song
  const currentSong = songs[activeSongIndex] || DEFAULT_SETLIST[0]

  // Global modals & drawer
  const [isJsonModalOpen, setIsJsonModalOpen] = useState(false)
  const [jsonModalTab, setJsonModalTab] = useState<'export' | 'import'>('export')
  const [isHeaderKeyPickerOpen, setIsHeaderKeyPickerOpen] = useState(false)
  const [isSetlistDrawerOpen, setIsSetlistDrawerOpen] = useState(false)

  // Transpose handler
  const handleTransposeChange = (newOffset: number) => {
    setSongs((prev) =>
      prev.map((s, idx) => (idx === activeSongIndex ? { ...s, transposeOffset: newOffset } : s))
    )
  }

  // Delete song from library state with safe index adjustment
  const handleDeleteSong = (indexToDelete: number) => {
    if (songs.length <= 1) {
      // If last remaining song is deleted, reset to blank template
      const blankSong: ActiveSongState = {
        id: Date.now(),
        title: 'New Song',
        artist: '',
        key: 'G',
        capo: 'No Capo',
        bpm: '120',
        format: 'CHORD_PRO',
        transposeOffset: 0,
        rawContent: `{title: New Song}\n{artist: }\n{key: G}\n{capo: No Capo}\n{tempo: 120}\n\n[Intro]\n\n[Verse 1]\n\n[Chorus]\n`,
      }
      setSongs([blankSong])
      setActiveSongIndex(0)
      return
    }

    const updatedSongs = songs.filter((_, idx) => idx !== indexToDelete)
    setSongs(updatedSongs)
    if (activeSongIndex === indexToDelete) {
      setActiveSongIndex(Math.min(indexToDelete, updatedSongs.length - 1))
    } else if (activeSongIndex > indexToDelete) {
      setActiveSongIndex(activeSongIndex - 1)
    }
  }

  // Create new blank song template and switch to Desktop Editor
  const handleNewSong = () => {
    const blankSong: ActiveSongState = {
      id: Date.now(),
      title: 'New Song',
      artist: '',
      key: 'G',
      capo: 'No Capo',
      bpm: '120',
      format: 'CHORD_PRO',
      transposeOffset: 0,
      rawContent: `{title: New Song}\n{artist: }\n{key: G}\n{capo: No Capo}\n{tempo: 120}\n\n[Intro]\n\n[Verse 1]\n\n[Chorus]\n`,
    }
    setSongs((prev) => [blankSong, ...prev])
    setActiveSongIndex(0)
    setActiveView('editor')
    setIsSetlistDrawerOpen(false)
  }

  // Update song fields in editor
  const handleUpdateSong = (updated: Partial<ActiveSongState>) => {
    setSongs((prev) =>
      prev.map((s, idx) => {
        if (idx !== activeSongIndex) return s
        const next = { ...s, ...updated }
        if (updated.rawContent !== undefined) {
          const meta = extractDirectives(updated.rawContent)
          if (meta.title) next.title = meta.title
          if (meta.artist) next.artist = meta.artist
          if (meta.key) next.key = meta.key
          if (meta.capo) next.capo = meta.capo
          if (meta.bpm) next.bpm = meta.bpm
        }
        return next
      })
    )
  }

  // Load sample song
  const handleLoadSampleSong = (sample: typeof SAMPLE_SONGS.standByMe) => {
    const newSong: ActiveSongState = {
      id: Date.now(),
      title: sample.title,
      artist: sample.artist,
      key: sample.key,
      capo: sample.capo,
      bpm: sample.bpm,
      rawContent: sample.rawContent,
      format: sample.format,
      transposeOffset: 0,
    }
    setSongs((prev) => [newSong, ...prev])
    setActiveSongIndex(0)
  }

  // Import song
  const handleImportSong = (imported: Partial<ActiveSongState>) => {
    const newSong: ActiveSongState = {
      id: Date.now(),
      title: imported.title || 'Imported Song',
      artist: imported.artist || '',
      key: imported.key || 'G',
      capo: imported.capo || '',
      bpm: imported.bpm || '120',
      rawContent: imported.rawContent || '',
      format: imported.format || 'CHORD_PRO',
      transposeOffset: imported.transposeOffset || 0,
    }
    setSongs((prev) => [newSong, ...prev])
    setActiveSongIndex(0)
  }

  const handleLockApp = () => {
    sessionStorage.removeItem('gtar_authenticated')
    setIsUnlocked(false)
  }

  if (!isUnlocked) {
    return <LoginWall onUnlock={() => setIsUnlocked(true)} />
  }

  return (
    <div className="min-h-screen flex flex-col bg-[#002B36] text-[#EEE8D5]">
      {/* Top Stage Header */}
      <Header
        activeView={activeView}
        onViewChange={setActiveView}
        song={currentSong}
        songsCount={songs.length}
        activeSongIndex={activeSongIndex}
        transposeOffset={currentSong.transposeOffset || 0}
        onTransposeChange={handleTransposeChange}
        onOpenKeyPicker={() => setIsHeaderKeyPickerOpen(true)}
        onOpenSetlistDrawer={() => setIsSetlistDrawerOpen(true)}
        onOpenExportModal={() => {
          setJsonModalTab('export')
          setIsJsonModalOpen(true)
        }}
        onOpenImportModal={() => {
          setJsonModalTab('import')
          setIsJsonModalOpen(true)
        }}
        onLoadSong={handleLoadSampleSong}
        onLockApp={handleLockApp}
      />

      {/* Main Workspace: Split Desktop Editor vs 1:1 Stage View */}
      <main className="flex-1 flex overflow-hidden">
        {activeView === 'editor' ? (
          <DesktopEditor
            song={currentSong}
            onUpdateSong={handleUpdateSong}
            transposeOffset={currentSong.transposeOffset || 0}
          />
        ) : (
          <StageView
            song={currentSong}
            songs={songs}
            activeSongIndex={activeSongIndex}
            onSelectSongIndex={setActiveSongIndex}
            onOpenSetlistDrawer={() => setIsSetlistDrawerOpen(true)}
            transposeOffset={currentSong.transposeOffset || 0}
            onTransposeChange={handleTransposeChange}
          />
        )}
      </main>

      {/* Slide-over Setlist / Library Drawer */}
      <SetlistDrawer
        isOpen={isSetlistDrawerOpen}
        onClose={() => setIsSetlistDrawerOpen(false)}
        songs={songs}
        activeSongIndex={activeSongIndex}
        onSelectSongIndex={(idx) => {
          setActiveSongIndex(idx)
          setIsSetlistDrawerOpen(false)
        }}
        onDeleteSong={handleDeleteSong}
        onNewSong={handleNewSong}
      />

      {/* JSON Import / Export Bridge Modal */}
      <JsonBridgeModal
        isOpen={isJsonModalOpen}
        initialTab={jsonModalTab}
        onClose={() => setIsJsonModalOpen(false)}
        song={currentSong}
        onImportSong={handleImportSong}
      />

      {/* Header Key Picker Modal */}
      <KeyPickerModal
        isOpen={isHeaderKeyPickerOpen}
        onClose={() => setIsHeaderKeyPickerOpen(false)}
        originalKey={currentSong.key}
        currentOffset={currentSong.transposeOffset || 0}
        capoText={currentSong.capo}
        onSelectOffset={handleTransposeChange}
        onReset={() => handleTransposeChange(0)}
      />
    </div>
  )
}

export default App
