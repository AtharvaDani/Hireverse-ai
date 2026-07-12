import { useState } from 'react'
import { callAgentDirectly } from '../services/api'

const VARIANTS = [
  { key:'professional', label:'Professional', emoji:'💼',
    desc:'Formal, structured, traditional tone', color:'#06b6d4' },
  { key:'bold',         label:'Bold',         emoji:'⚡',
    desc:'Confident, assertive, memorable opener', color:'#7c3aed' },
  { key:'storytelling', label:'Storytelling', emoji:'📖',
    desc:'Warm narrative, personal story opener', color:'#f59e0b' },
]

function CoverCard({ variant, data }) {
  const [copied, setCopied] = useState(false)

  function copy() {
    const text = `Subject: ${data.subject}\n\n${data.body}`
    navigator.clipboard.writeText(text)
    setCopied(true)
    setTimeout(() => setCopied(false), 2000)
  }

  return (
    <div style={{
      border:`1px solid ${variant.color}33`,
      background:`${variant.color}06`,
      borderRadius:'var(--radius-lg)', overflow:'hidden'
    }}>
      {/* Header */}
      <div style={{ padding:'16px 20px', borderBottom:`1px solid ${variant.color}22`,
        display:'flex', alignItems:'center', gap:10 }}>
        <span style={{ fontSize:20 }}>{variant.emoji}</span>
        <div style={{ flex:1 }}>
          <div style={{ fontSize:14, fontWeight:700, color:'var(--text)' }}>
            {variant.label}
          </div>
          <div style={{ fontSize:12, color:'var(--text-muted)' }}>{variant.desc}</div>
        </div>
        <button onClick={copy} style={{
          padding:'6px 14px', borderRadius:'var(--radius-sm)', border:'none', cursor:'pointer',
          background: copied ? 'rgba(16,185,129,0.2)' : `${variant.color}20`,
          color: copied ? 'var(--emerald)' : variant.color,
          fontSize:12, fontWeight:600, transition:'all .2s'
        }}>
          {copied ? '✓ Copied!' : 'Copy'}
        </button>
      </div>

      {/* Subject line */}
      <div style={{ padding:'12px 20px',
        borderBottom:`1px solid ${variant.color}15`,
        background:`${variant.color}04` }}>
        <span style={{ fontSize:11, fontWeight:700, letterSpacing:'.08em',
          textTransform:'uppercase', color:variant.color }}>Subject: </span>
        <span style={{ fontSize:13, color:'var(--text-soft)' }}>{data.subject}</span>
      </div>

      {/* Body */}
      <div style={{ padding:'20px', fontSize:14, lineHeight:1.8,
        color:'var(--text-soft)', whiteSpace:'pre-wrap' }}>
        {data.body}
      </div>
    </div>
  )
}

export default function CoverLetter({ resumeId }) {
  const [jobDesc, setJobDesc]   = useState('')
  const [data,    setData]      = useState(null)
  const [active,  setActive]    = useState('professional')
  const [loading, setLoading]   = useState(false)
  const [error,   setError]     = useState(null)

  async function generate() {
    if (!jobDesc.trim()) { setError('Paste a job description for best results.'); return }
    setLoading(true); setError(null); setData(null)
    try {
      const r = await callAgentDirectly(resumeId, 'COVER_LETTER_AGENT', null, jobDesc.trim())
      const raw = r.response.replace(/```json|```/g,'').trim()
      setData(JSON.parse(raw))
    } catch (e) {
      setError(e.response?.data?.error || 'Failed to generate cover letters. Try again.')
    } finally { setLoading(false) }
  }

  const activeVariant = VARIANTS.find(v => v.key === active)

  return (
    <div style={{ display:'flex', flexDirection:'column', gap:20 }}>
      <div>
        <h2 style={{ fontFamily:'var(--font-display)', fontSize:24, fontWeight:700,
          margin:'0 0 8px',
          background:'linear-gradient(135deg,#fff,#06b6d4)',
          WebkitBackgroundClip:'text', WebkitTextFillColor:'transparent' }}>
          Cover Letter Generator
        </h2>
        <p style={{ fontSize:14, color:'var(--text-soft)', margin:0, lineHeight:1.6 }}>
          Paste a job description and get 3 tailored cover letters instantly —
          professional, bold, and storytelling. All grounded in your actual resume.
        </p>
      </div>

      <textarea value={jobDesc} onChange={e => setJobDesc(e.target.value)}
        placeholder="Paste the job description here (required for best results)…"
        className="agent-tab-textarea" disabled={loading} style={{ minHeight:120 }}/>

      <button onClick={generate} disabled={loading} className="agent-tab-button">
        {loading
          ? <><span className="spinner"/> Writing your cover letters…</>
          : '✍️ Generate 3 cover letters'}
      </button>

      {loading && (
        <div className="agent-tab-skeleton">
          {[75,92,60,85,45,70,55].map((w,i) => (
            <div key={i} className="skeleton-line" style={{ width:`${w}%` }}/>
          ))}
        </div>
      )}

      {error && <div className="agent-tab-error">{error}</div>}

      {data && (
        <div style={{ display:'flex', flexDirection:'column', gap:16 }}>
          {/* Key points used */}
          {data.keyPointsUsed?.length > 0 && (
            <div style={{ padding:'12px 16px',
              background:'rgba(16,185,129,0.06)',
              border:'1px solid rgba(16,185,129,0.2)',
              borderRadius:'var(--radius-md)',
              display:'flex', gap:10, flexWrap:'wrap', alignItems:'center' }}>
              <span style={{ fontSize:12, fontWeight:700,
                color:'var(--emerald)', flexShrink:0 }}>
                Resume points used:
              </span>
              {data.keyPointsUsed.map(k => (
                <span key={k} style={{ fontSize:12,
                  padding:'2px 10px', borderRadius:100,
                  background:'rgba(16,185,129,0.12)',
                  border:'1px solid rgba(16,185,129,0.25)',
                  color:'var(--text-soft)' }}>{k}</span>
              ))}
            </div>
          )}

          {/* Variant tabs */}
          <div style={{ display:'flex', gap:6 }}>
            {VARIANTS.map(v => (
              <button key={v.key} onClick={() => setActive(v.key)} style={{
                flex:1, padding:'10px 12px', borderRadius:'var(--radius-md)',
                border:`1px solid ${active===v.key ? v.color+'66' : 'var(--glass-border)'}`,
                background: active===v.key ? `${v.color}15` : 'transparent',
                color: active===v.key ? v.color : 'var(--text-muted)',
                fontSize:13, fontWeight:600, cursor:'pointer', transition:'all .2s',
                fontFamily:'var(--font-body)'
              }}>
                {v.emoji} {v.label}
              </button>
            ))}
          </div>

          {/* Active cover letter */}
          {data[active] && (
            <CoverCard
              key={active}
              variant={activeVariant}
              data={data[active]}
            />
          )}

          <button onClick={generate} disabled={loading}
            style={{ alignSelf:'flex-start' }} className="btn-ghost">
            ↻ Regenerate
          </button>
        </div>
      )}
    </div>
  )
}
