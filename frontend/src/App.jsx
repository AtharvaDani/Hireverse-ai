import { useState, useEffect, useRef } from 'react'
import ResumeUpload from './components/ResumeUpload.jsx'
import Report from './components/Report.jsx'
import AgentTab from './components/AgentTab.jsx'
import MockInterview from './components/MockInterview.jsx'
import JobSearch from './components/JobSearch.jsx'
import ResumeDna from './components/ResumeDna.jsx'
import CareerTrajectory from './components/CareerTrajectory.jsx'
import CoverLetter from './components/CoverLetter.jsx'

const TABS = [
  { id:'chat',       label:'Chat',            glyph:'✦',  group:'core'  },
  { id:'dna',        label:'DNA Profile',     glyph:'🧬', group:'core'  },
  { id:'score',      label:'Score',           glyph:'◐',  group:'core'  },
  { id:'ats',        label:'Keyword Gaps',    glyph:'⊕',  group:'tools' },
  { id:'rewrite',    label:'Rewrite',         glyph:'✎',  group:'tools' },
  { id:'cover',      label:'Cover Letter',    glyph:'✉',  group:'tools' },
  { id:'trajectory', label:'Career Path',     glyph:'🔮', group:'tools' },
  { id:'mock',       label:'Mock Interview',  glyph:'🎙', group:'tools' },
  { id:'jobs',       label:'Job Search',      glyph:'⌕',  group:'tools' },
]

/* ── Animated particle canvas ── */
function ParticleCanvas() {
  const canvasRef = useRef(null)
  useEffect(() => {
    const canvas = canvasRef.current
    if (!canvas) return
    const ctx = canvas.getContext('2d')
    let raf, W = canvas.width = window.innerWidth, H = canvas.height = window.innerHeight
    const COLORS = ['#7c3aed','#06b6d4','#10b981','#f59e0b']
    const particles = Array.from({ length: 70 }, () => ({
      x: Math.random()*W, y: Math.random()*H,
      r: Math.random()*1.8+0.3,
      vx: (Math.random()-.5)*.35, vy: (Math.random()-.5)*.35,
      color: COLORS[Math.floor(Math.random()*COLORS.length)],
      alpha: Math.random()*.5+.1,
    }))
    function draw() {
      ctx.clearRect(0,0,W,H)
      // grid
      ctx.strokeStyle='rgba(255,255,255,0.022)'; ctx.lineWidth=1
      for (let x=0;x<W;x+=80){ ctx.beginPath();ctx.moveTo(x,0);ctx.lineTo(x,H);ctx.stroke() }
      for (let y=0;y<H;y+=80){ ctx.beginPath();ctx.moveTo(0,y);ctx.lineTo(W,y);ctx.stroke() }
      // glow orbs
      const g1=ctx.createRadialGradient(W*.15,H*.25,0,W*.15,H*.25,350)
      g1.addColorStop(0,'rgba(124,58,237,0.13)');g1.addColorStop(1,'transparent')
      ctx.fillStyle=g1;ctx.fillRect(0,0,W,H)
      const g2=ctx.createRadialGradient(W*.85,H*.7,0,W*.85,H*.7,280)
      g2.addColorStop(0,'rgba(6,182,212,0.09)');g2.addColorStop(1,'transparent')
      ctx.fillStyle=g2;ctx.fillRect(0,0,W,H)
      // particles
      particles.forEach(p=>{
        p.x+=p.vx;p.y+=p.vy
        if(p.x<0)p.x=W;if(p.x>W)p.x=0;if(p.y<0)p.y=H;if(p.y>H)p.y=0
        ctx.beginPath();ctx.arc(p.x,p.y,p.r,0,Math.PI*2)
        ctx.fillStyle=p.color+Math.round(p.alpha*255).toString(16).padStart(2,'0')
        ctx.fill()
      })
      raf=requestAnimationFrame(draw)
    }
    draw()
    const onResize=()=>{ W=canvas.width=window.innerWidth; H=canvas.height=window.innerHeight }
    window.addEventListener('resize',onResize)
    return ()=>{ cancelAnimationFrame(raf); window.removeEventListener('resize',onResize) }
  },[])
  return <canvas ref={canvasRef} style={{position:'fixed',inset:0,pointerEvents:'none',zIndex:0}}/>
}

/* ── 3D tilt wrapper ── */
function TiltCard({ children, className, style }) {
  const ref = useRef(null)
  function onMove(e) {
    const el=ref.current; if(!el) return
    const rect=el.getBoundingClientRect()
    const dx=(e.clientX-rect.left-rect.width/2)/(rect.width/2)
    const dy=(e.clientY-rect.top-rect.height/2)/(rect.height/2)
    el.style.transform=`perspective(900px) rotateY(${dx*5}deg) rotateX(${-dy*5}deg) translateZ(6px)`
  }
  function onLeave() { if(ref.current) ref.current.style.transform='perspective(900px) rotateY(0) rotateX(0)' }
  return (
    <div ref={ref} className={className}
      style={{...style, transition:'transform .18s ease', willChange:'transform'}}
      onMouseMove={onMove} onMouseLeave={onLeave}>
      {children}
    </div>
  )
}

export default function App() {
  const [resumeInfo, setResumeInfo] = useState(null)
  const [activeTab,  setActiveTab]  = useState('chat')

  return (
    <>
      <ParticleCanvas/>
      <div className="app-shell">
        {/* Hero header */}
        <header className="app-header">
          <div className="app-header-mark">AI-Powered Career Assistant</div>
          <h1 className="app-title">Land your<br/><em>dream role</em>.</h1>
          <p className="app-subtitle">
            Upload your résumé once. Get a DNA profile, career trajectory,
            AI score, keyword gaps, cover letters, mock interview coaching,
            and live job matches — all free.
          </p>
        </header>

        <main style={{ display:'flex', flexDirection:'column', gap:16 }}>
          <TiltCard className="upload-card">
            <ResumeUpload onUploadSuccess={info => { setResumeInfo(info); setActiveTab('dna') }}/>
          </TiltCard>

          {resumeInfo && (
            <div className="workspace">
              <div className="status-bar">
                <span className="status-dot"/>
                <span><strong>{resumeInfo.fileName}</strong> is indexed and ready.</span>
              </div>

              {/* Tab groups */}
              <nav style={{ display:'flex', flexDirection:'column', gap:6 }}>
                {/* Core tabs */}
                <div style={{ fontSize:10, letterSpacing:'.12em', textTransform:'uppercase',
                  color:'var(--text-muted)', padding:'0 4px', marginBottom:2 }}>
                  Core
                </div>
                <div className="tab-rail">
                  {TABS.filter(t=>t.group==='core').map(tab=>(
                    <button key={tab.id} onClick={()=>setActiveTab(tab.id)}
                      className={`tab-rail-item ${activeTab===tab.id?'is-active':''}`}>
                      <span className="tab-rail-glyph">{tab.glyph}</span>
                      {tab.label}
                    </button>
                  ))}
                </div>
                {/* Tools tabs */}
                <div style={{ fontSize:10, letterSpacing:'.12em', textTransform:'uppercase',
                  color:'var(--text-muted)', padding:'4px 4px 2px' }}>
                  Tools
                </div>
                <div className="tab-rail">
                  {TABS.filter(t=>t.group==='tools').map(tab=>(
                    <button key={tab.id} onClick={()=>setActiveTab(tab.id)}
                      className={`tab-rail-item ${activeTab===tab.id?'is-active':''}`}>
                      <span className="tab-rail-glyph">{tab.glyph}</span>
                      {tab.label}
                    </button>
                  ))}
                </div>
              </nav>

              {/* Tab panel */}
              <div className="tab-panel" key={activeTab}>
                {activeTab==='chat'       && <Report resumeId={resumeInfo.resumeId}/>}
                {activeTab==='dna'        && <ResumeDna resumeId={resumeInfo.resumeId}/>}
                {activeTab==='score'      && (
                  <AgentTab resumeId={resumeInfo.resumeId} agentType="SCORE_AGENT"
                    title="Résumé score" variant="score"
                    description="An honest 0–100 score broken down by clarity, impact, ATS-friendliness, and structure."
                    buttonLabel="Score my résumé"/>
                )}
                {activeTab==='ats'        && (
                  <AgentTab resumeId={resumeInfo.resumeId} agentType="ATS_GAP_AGENT"
                    title="ATS keyword gaps"
                    description="Paste a job description. We'll show exactly which keywords are missing from your résumé."
                    buttonLabel="Check keyword gaps" needsJobDescription jobDescriptionRequired/>
                )}
                {activeTab==='rewrite'    && (
                  <AgentTab resumeId={resumeInfo.resumeId} agentType="REWRITE_AGENT"
                    title="Rewrite optimizer"
                    description="Weak bullet points get stronger verbs and clearer impact — shown as before and after."
                    buttonLabel="Optimize my bullet points" needsJobDescription jobDescriptionRequired={false}/>
                )}
                {activeTab==='cover'      && <CoverLetter resumeId={resumeInfo.resumeId}/>}
                {activeTab==='trajectory' && <CareerTrajectory resumeId={resumeInfo.resumeId}/>}
                {activeTab==='mock'       && <MockInterview resumeId={resumeInfo.resumeId}/>}
                {activeTab==='jobs'       && <JobSearch resumeId={resumeInfo.resumeId}/>}
              </div>
            </div>
          )}
        </main>
      </div>
    </>
  )
}
