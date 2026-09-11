const {test}=require('node:test')
const assert=require('node:assert/strict')
const fs=require('node:fs'),ts=require('typescript')
for(const ext of ['.ts','.tsx'])require.extensions[ext]=(module,filename)=>module._compile(ts.transpileModule(fs.readFileSync(filename,'utf8').replaceAll('import.meta.env','({DEV:false,VITE_GOOGLE_CLIENT_ID:"client"})'),{compilerOptions:{module:ts.ModuleKind.CommonJS,target:ts.ScriptTarget.ES2022,jsx:ts.JsxEmit.ReactJSX,esModuleInterop:true}}).outputText,filename)
const {JSDOM}=require('jsdom'),React=require('react'),{createRoot}=require('react-dom/client')
const {act}=React
const {openSyncJournal,persistLibrary,readPersistedLibrary}=require('../src/utils/syncJournal.ts')
const {normalizeBackupSong}=require('../src/utils/jsonBackup.ts')
const {useDriveSync}=require('../src/hooks/useDriveSync.ts')
const {AuthGate}=require('../src/components/AuthGate.tsx')

test('real sync hook preserves an offline edit through failed upload and remount, then reconnects',async()=>{
 const dom=new JSDOM('<div id="root"></div>',{url:'https://gtar-web.pages.dev'})
 const previous={window:global.window,document:global.document,localStorage:global.localStorage,sessionStorage:global.sessionStorage,fetch:global.fetch}
 const online=Object.getOwnPropertyDescriptor(navigator,'onLine')
 Object.assign(global,{window:dom.window,document:dom.window.document,localStorage:dom.window.localStorage,sessionStorage:dom.window.sessionStorage,IS_REACT_ACT_ENVIRONMENT:true})
 Object.defineProperty(navigator,'onLine',{configurable:true,value:true});window.google={}
 const base={songs:[normalizeBackupSong({id:'A',title:'Song',rawContent:'original'})],setlists:[]}
 const edited={...base,songs:[{...base.songs[0],rawContent:'unsent latest'}]}
 const j=openSyncJournal('owner',base);j.prepare(base,base);j.acknowledge();j.complete();persistLibrary(edited)
 sessionStorage.setItem('gtar_google_session',JSON.stringify({token:'test',expiresAt:Date.now()+60000,user:{sub:'owner',email:'jlopez3rd@gmail.com'}}))
 let fail=true,uploads=0,controls
 const file={id:'legacy',name:'gtar_songbook_sync.json',version:'1'}
 global.fetch=async(url,init)=>{
  if(url.includes('/userinfo'))return new Response(JSON.stringify({sub:'owner',email:'jlopez3rd@gmail.com',email_verified:true}))
  const params=new URL(url).searchParams
  if(params.has('uploadType')){uploads++;if(fail)throw Error('Upload failed');assert.match(init.body,/unsent latest/);return new Response(JSON.stringify({id:'revision',version:'1'}))}
  return new Response(JSON.stringify(params.has('alt')?base:params.has('spaces')?{files:[file]}:file))
 }
 function Library(){const [state,setState]=React.useState(()=>readPersistedLibrary());controls=useDriveSync(state,setState);return React.createElement('p',null,controls.status)}
 let root=createRoot(document.getElementById('root'))
 async function mount(){await act(async()=>root.render(React.createElement(AuthGate,null,React.createElement(Library))));await act(async()=>{await new Promise(r=>setTimeout(r,30))})}
 try{
  await mount()
  assert.ok(uploads>0);assert.match(controls.status,/Upload failed/)
  assert.equal(readPersistedLibrary().songs[0].rawContent,'unsent latest')
  await act(async()=>root.unmount())
  fail=false;root=createRoot(document.getElementById('root'));await mount()
  assert.match(controls.status,/Synced/)
  assert.equal(readPersistedLibrary().songs[0].rawContent,'unsent latest')
  assert.equal(openSyncJournal('owner',edited).baseline.songs[0].rawContent,'unsent latest')
 }finally{await act(async()=>root.unmount());dom.window.close();Object.assign(global,previous);if(online)Object.defineProperty(navigator,'onLine',online);else delete navigator.onLine;delete global.IS_REACT_ACT_ENVIRONMENT}
})
