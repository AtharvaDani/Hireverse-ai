import { useState } from 'react'
import { callAgentDirectly } from '../services/api'

function PathCard({ path, index }) {
  const [open, setOpen] = useState(index === 0)
  return (
    <div style={{
      border:`1px solid ${path.color}44`,
      borderRadius:'var(--radius-lg)',
      background:`${path.color}08`,
      overflow:'hidden',
      transition:'box-shadow .2s',
      boxShadow: open ? `0 0 30px ${path.color}22` : 'none'
    }}>
      {/* Header */}
      <button onClick={() => setOpen(o=>!o)} style={{
        width:'100%', padding:'18px 20px',
        background:'transparent', border:'none', cursor:'pointer',
        display:'flex', alignItems:'center', gap:14, textAlign:'left'
      }}>
        {/* Probability ring */}
        <div style={{ position:'relative', width:52, height:52, flexShrink:0 }}>
          <svg width="52" height="52" viewBox="0 0 52 52">
            <circle cx="26" cy="26" r="22" fill="none"
              stroke="rgba(255,255,255,0.08)" strokeWidth="4"/>
            <circle cx="26" cy="26" r="22" fill="none"
              stroke={path.color} strokeWidth="4" strokeLinecap="round"
              strokeDasharray={2*Math.PI*22}
              strokeDashoffset={2*Math.PI*22 - (path.probability/100)*2*Math.PI*22}
              transform="rotate(-90 26 26)"
              style={{ filter:`drop-shadow(0 0 6px ${path.color})` }}/>
          </svg>
          <span style={{
            position:'absolute', inset:0,
            display:'flex', alignItems:'center', justifyContent:'center',
            fontFamily:'var(--font-mono)', fontSize:11, fontWeight:700, color:path.color
          }}>{path.probability}%</span>
        </div>

        <div style={{ flex:1 }}>
          <div style={{ fontSize:15, fontWeight:700, color:'var(--text)', marginBottom:3 }}>
            {path.title}
          </div>
          <div style={{ fontSize:12, color:'var(--text-muted)' }}>
            ⏱ {path.timeline} &nbsp;·&nbsp; {path.probability}% probability
          </div>
        </div>
        <span style={{ color:'var(--text-muted)', fontSize:18,
          transform: open ? 'rotate(90deg)':'none', transition:'transform .2s' }}>›</span>
      </button>

      {/* Body */}
      {open && (
        <div style={{ padding:'0 20px 20px', display:'flex', flexDirection:'column', gap:16,
          animation:'fade-up .25s ease' }}>
          <div>
            <div style={{ fontSize:11, fontWeight:700, letterSpacing:'.1em',
              textTransform:'uppercase', color:path.color, marginBottom:8 }}>
              Skills to acquire
            </div>
            <div style={{ display:'flex', flexWrap:'wrap', gap:6 }}>
              {path.skillsNeeded.map(s => (
                <span key={s} style={{
                  padding:'4px 12px', borderRadius:100, fontSize:12, fontWeight:500,
                  background:`${path.color}15`, border:`1px solid ${path.color}33`,
                  color:'var(--text-soft)'
                }}>{s}</span>
              ))}
            </div>
          </div>

          <div>
            <div style={{ fontSize:11, fontWeight:700, letterSpacing:'.1em',
              textTransform:'uppercase', color:path.color, marginBottom:8 }}>
              Action plan
            </div>
            <div style={{ display:'flex', flexDirection:'column', gap:8 }}>
              {path.actionPlan.map((a,i) => (
                <div key={i} style={{ display:'flex', gap:10, alignItems:'flex-start' }}>
                  <span style={{
                    width:22, height:22, borderRadius:'50%', flexShrink:0,
                    background:`${path.color}20`, border:`1px solid ${path.color}44`,
                    display:'flex', alignItems:'center', justifyContent:'center',
                    fontSize:11, fontWeight:700, color:path.color
                  }}>{i+1}</span>
                  <span style={{ fontSize:13, color:'var(--text-soft)', lineHeight:1.5 }}>{a}</span>
                </div>
              ))}
            </div>
          </div>
        </div>
      )}
    </div>
  )
}

export default function CareerTrajectory({ resumeId }) {
  const [jobDesc, setJobDesc]   = useState('')
  const [data,    setData]      = useState(null)
  const [loading, setLoading]   = useState(false)
  const [error,   setError]     = useState(null)

  async function predict() {
    setLoading(true); setError(null); setData(null)
    try {
      const r = await callAgentDirectly(resumeId, 'TRAJECTORY_AGENT', null, jobDesc.trim())
      const raw = r.response.replace(/```json|```/g,'').trim()
      setData(JSON.parse(raw))
    } catch (e) {
      setError(e.response?.data?.error || 'Failed to generate trajectory. Try again.')
    } finally { setLoading(false) }
  }

  return (
    <div style={{ display:'flex', flexDirection:'column', gap:20 }}>
      <div>
        <h2 style={{ fontFamily:'var(--font-display)', fontSize:24, fontWeight:700,
          margin:'0 0 8px',
          background:'linear-gradient(135deg,#fff,#f59e0b)',
          WebkitBackgroundClip:'text', WebkitTextFillColor:'transparent' }}>
          Career Trajectory
        </h2>
        <p style={{ fontSize:14, color:'var(--text-soft)', margin:0, lineHeight:1.6 }}>
          AI predicts 3 realistic paths from where you are now — with timelines,
          skill gaps, and a concrete action plan for each.
        </p>
      </div>

      <textarea value={jobDesc} onChange={e => setJobDesc(e.target.value)}
        placeholder="Optional: paste a target job description to get a tailored prediction…"
        className="agent-tab-textarea" disabled={loading} style={{ minHeight:80 }}/>

      {!data && (
        <button onClick={predict} disabled={loading} className="agent-tab-button">
          {loading
            ? <><span className="spinner"/> Predicting your future…</>
            : '🔮 Predict my career trajectory'}
        </button>
      )}

      {loading && (
        <div className="agent-tab-skeleton">
          {[70,90,55,80,40,65].map((w,i) => (
            <div key={i} className="skeleton-line" style={{ width:`${w}%` }}/>
          ))}
        </div>
      )}

      {error && <div className="agent-tab-error">{error}</div>}

      {data && (
        <div style={{ display:'flex', flexDirection:'column', gap:20 }}>
          {/* Current level */}
          <div style={{ display:'flex', gap:12, alignItems:'center',
            background:'rgba(255,255,255,0.03)',
            border:'1px solid var(--glass-border)',
            borderRadius:'var(--radius-md)', padding:'14px 18px' }}>
            <span style={{ fontSize:24 }}>📍</span>
            <div>
              <div style={{ fontSize:11, color:'var(--text-muted)',
                letterSpacing:'.1em', textTransform:'uppercase', marginBottom:2 }}>
                You are here
              </div>
              <div style={{ fontSize:15, fontWeight:600, color:'var(--text)' }}>
                {data.currentLevel}
                {data.yearsExperience > 0 &&
                  <span style={{ color:'var(--text-muted)', fontWeight:400,
                    fontSize:13, marginLeft:8 }}>
                    · {data.yearsExperience} yrs exp
                  </span>}
              </div>
            </div>
            {data.salaryRange && (
              <div style={{ marginLeft:'auto', textAlign:'right' }}>
                <div style={{ fontSize:10, color:'var(--text-muted)',
                  textTransform:'uppercase', letterSpacing:'.08em', marginBottom:2 }}>
                  Market range
                </div>
                <div style={{ fontSize:13, fontWeight:600,
                  color:'var(--emerald)' }}>{data.salaryRange}</div>
              </div>
            )}
          </div>

          {/* Path cards */}
          <div style={{ display:'flex', flexDirection:'column', gap:10 }}>
            {data.paths.map((p,i) => <PathCard key={i} path={p} index={i}/>)}
          </div>

          {/* Immediate actions */}
          {data.immediateActions?.length > 0 && (
            <div style={{
              background:'rgba(6,182,212,0.06)',
              border:'1px solid rgba(6,182,212,0.2)',
              borderRadius:'var(--radius-lg)', padding:20
            }}>
              <div style={{ fontSize:11, fontWeight:700, letterSpacing:'.1em',
                textTransform:'uppercase', color:'var(--cyan)', marginBottom:12 }}>
                ⚡ Do this week
              </div>
              <div style={{ display:'flex', flexDirection:'column', gap:8 }}>
                {data.immediateActions.map((a,i) => (
                  <div key={i} style={{ display:'flex', gap:10, alignItems:'flex-start' }}>
                    <span style={{ color:'var(--cyan)', fontWeight:700, flexShrink:0 }}>
                      {i+1}.
                    </span>
                    <span style={{ fontSize:13, color:'var(--text-soft)', lineHeight:1.5 }}>
                      {a}
                    </span>
                  </div>
                ))}
              </div>
            </div>
          )}

          <button onClick={predict} disabled={loading}
            style={{ alignSelf:'flex-start' }} className="btn-ghost">
            ↻ Re-predict
          </button>
        </div>
      )}
    </div>
  )
}
