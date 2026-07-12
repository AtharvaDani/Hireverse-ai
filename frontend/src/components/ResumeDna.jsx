import { useState } from 'react'
import { callAgentDirectly } from '../services/api'

const DIM_COLORS = {
  'Technical Depth':  '#06b6d4',
  'Leadership':       '#7c3aed',
  'Communication':    '#10b981',
  'Impact & Results': '#f59e0b',
  'Clarity':          '#f43f5e',
  'ATS Readiness':    '#a855f7',
}

function RadarChart({ dimensions }) {
  const size = 280, cx = 140, cy = 140, r = 100
  const n = dimensions.length
  const points = dimensions.map((d, i) => {
    const angle = (i / n) * Math.PI * 2 - Math.PI / 2
    const sr = (d.score / 100) * r
    return {
      x: cx + Math.cos(angle) * sr,
      y: cy + Math.sin(angle) * sr,
      lx: cx + Math.cos(angle) * (r + 28),
      ly: cy + Math.sin(angle) * (r + 28),
      ...d
    }
  })

  // Grid rings
  const rings = [20, 40, 60, 80, 100]
  const axisPoints = dimensions.map((_, i) => {
    const angle = (i / n) * Math.PI * 2 - Math.PI / 2
    return { x: cx + Math.cos(angle) * r, y: cy + Math.sin(angle) * r }
  })

  const polyStr = points.map(p => `${p.x},${p.y}`).join(' ')

  return (
    <svg width={size} height={size} viewBox={`0 0 ${size} ${size}`}
      style={{ overflow:'visible' }}>
      <defs>
        <radialGradient id="radarGrad" cx="50%" cy="50%" r="50%">
          <stop offset="0%"   stopColor="#7c3aed" stopOpacity="0.25"/>
          <stop offset="100%" stopColor="#06b6d4" stopOpacity="0.05"/>
        </radialGradient>
        <filter id="glow">
          <feGaussianBlur stdDeviation="3" result="coloredBlur"/>
          <feMerge><feMergeNode in="coloredBlur"/><feMergeNode in="SourceGraphic"/></feMerge>
        </filter>
      </defs>

      {/* Grid rings */}
      {rings.map(pct => {
        const rr = (pct / 100) * r
        const pts = dimensions.map((_, i) => {
          const angle = (i / n) * Math.PI * 2 - Math.PI / 2
          return `${cx + Math.cos(angle) * rr},${cy + Math.sin(angle) * rr}`
        }).join(' ')
        return <polygon key={pct} points={pts}
          fill="none" stroke="rgba(255,255,255,0.06)" strokeWidth="1"/>
      })}

      {/* Axis lines */}
      {axisPoints.map((p, i) => (
        <line key={i} x1={cx} y1={cy} x2={p.x} y2={p.y}
          stroke="rgba(255,255,255,0.08)" strokeWidth="1"/>
      ))}

      {/* Filled polygon */}
      <polygon points={polyStr} fill="url(#radarGrad)"
        stroke="url(#radarGrad)" strokeWidth="2"/>

      {/* Glow polygon outline */}
      <polygon points={polyStr} fill="none"
        stroke="#7c3aed" strokeWidth="2" opacity="0.8" filter="url(#glow)"/>

      {/* Data points */}
      {points.map((p, i) => (
        <circle key={i} cx={p.x} cy={p.y} r="5"
          fill={DIM_COLORS[p.name] || '#7c3aed'}
          stroke="#050816" strokeWidth="2"
          filter="url(#glow)"/>
      ))}

      {/* Labels */}
      {points.map((p, i) => (
        <text key={i} x={p.lx} y={p.ly}
          textAnchor={p.lx > cx + 5 ? 'start' : p.lx < cx - 5 ? 'end' : 'middle'}
          dominantBaseline="middle"
          fill={DIM_COLORS[p.name] || '#9ca3c8'}
          fontSize="10" fontWeight="600" fontFamily="Space Grotesk, sans-serif">
          {p.name.split(' ')[0]}
        </text>
      ))}
    </svg>
  )
}

function DimBar({ dim, index }) {
  const color = DIM_COLORS[dim.name] || '#7c3aed'
  return (
    <div style={{ display:'flex', flexDirection:'column', gap:6,
      animation:`fade-up .4s ease ${index*0.08}s both` }}>
      <div style={{ display:'flex', justifyContent:'space-between', alignItems:'center' }}>
        <span style={{ fontSize:13, fontWeight:600, color:'var(--text)' }}>{dim.name}</span>
        <span style={{ fontFamily:'var(--font-mono)', fontSize:13,
          color, fontWeight:700 }}>{dim.score}</span>
      </div>
      <div style={{ height:6, background:'rgba(255,255,255,0.06)',
        borderRadius:3, overflow:'hidden' }}>
        <div style={{
          height:'100%', width:`${dim.score}%`, borderRadius:3,
          background:`linear-gradient(90deg, ${color}88, ${color})`,
          boxShadow:`0 0 8px ${color}66`,
          transition:'width 1s cubic-bezier(.34,1.56,.64,1)',
        }}/>
      </div>
      <p style={{ fontSize:12, color:'var(--text-muted)', margin:0 }}>{dim.reason}</p>
    </div>
  )
}

export default function ResumeDna({ resumeId }) {
  const [data,    setData]    = useState(null)
  const [loading, setLoading] = useState(false)
  const [error,   setError]   = useState(null)

  async function analyse() {
    setLoading(true); setError(null); setData(null)
    try {
      const r = await callAgentDirectly(resumeId, 'DNA_AGENT', null, null)
      const raw = r.response.replace(/```json|```/g,'').trim()
      setData(JSON.parse(raw))
    } catch (e) {
      setError(e.response?.data?.error || 'Failed to generate DNA profile. Try again.')
    } finally { setLoading(false) }
  }

  return (
    <div style={{ display:'flex', flexDirection:'column', gap:20 }}>
      {/* Header */}
      <div>
        <h2 style={{ fontFamily:'var(--font-display)', fontSize:24,
          fontWeight:700, margin:'0 0 8px',
          background:'linear-gradient(135deg, #fff, #c4b5fd)',
          WebkitBackgroundClip:'text', WebkitTextFillColor:'transparent' }}>
          Resume DNA Fingerprint
        </h2>
        <p style={{ fontSize:14, color:'var(--text-soft)', margin:0, lineHeight:1.6 }}>
          Your unique profile across 6 dimensions — technical depth, leadership,
          communication, impact, clarity, and ATS readiness. Visualised as a radar chart.
        </p>
      </div>

      {!data && (
        <button onClick={analyse} disabled={loading} className="agent-tab-button">
          {loading
            ? <><span className="spinner"/> Analysing your DNA…</>
            : '🧬 Generate my DNA Fingerprint'}
        </button>
      )}

      {loading && (
        <div style={{ display:'flex', flexDirection:'column', gap:10 }}>
          {[60,80,45,70,55].map((w,i) => (
            <div key={i} className="skeleton-line" style={{ width:`${w}%` }}/>
          ))}
        </div>
      )}

      {error && <div className="agent-tab-error">{error}</div>}

      {data && (
        <div style={{ display:'flex', flexDirection:'column', gap:24 }}>
          {/* Radar + top stats */}
          <div style={{ display:'flex', gap:24, flexWrap:'wrap', alignItems:'center' }}>
            <div style={{ flex:'0 0 auto', display:'flex', justifyContent:'center' }}>
              <RadarChart dimensions={data.dimensions}/>
            </div>
            <div style={{ flex:1, minWidth:200, display:'flex', flexDirection:'column', gap:12 }}>
              {/* Overall score */}
              <div style={{
                background:'rgba(124,58,237,0.1)',
                border:'1px solid rgba(124,58,237,0.25)',
                borderRadius:'var(--radius-md)', padding:'16px 20px'
              }}>
                <div style={{ fontSize:11, letterSpacing:'.1em', textTransform:'uppercase',
                  color:'var(--text-muted)', marginBottom:4 }}>Overall Score</div>
                <div style={{ fontFamily:'var(--font-display)', fontSize:42, fontWeight:800,
                  background:'linear-gradient(135deg,#fff,#c4b5fd)',
                  WebkitBackgroundClip:'text', WebkitTextFillColor:'transparent' }}>
                  {Math.round(data.dimensions.reduce((s,d) => s+d.score,0)/data.dimensions.length)}
                </div>
              </div>

              {/* Top strength / biggest gap */}
              <div style={{ display:'flex', gap:10 }}>
                <div style={{ flex:1, background:'rgba(16,185,129,0.08)',
                  border:'1px solid rgba(16,185,129,0.2)',
                  borderRadius:'var(--radius-md)', padding:'12px 14px' }}>
                  <div style={{ fontSize:10, color:'var(--emerald)',
                    fontWeight:700, letterSpacing:'.08em', textTransform:'uppercase', marginBottom:4 }}>
                    Top Strength
                  </div>
                  <div style={{ fontSize:13, fontWeight:600, color:'var(--text)' }}>
                    {data.topStrength}
                  </div>
                </div>
                <div style={{ flex:1, background:'rgba(244,63,94,0.08)',
                  border:'1px solid rgba(244,63,94,0.2)',
                  borderRadius:'var(--radius-md)', padding:'12px 14px' }}>
                  <div style={{ fontSize:10, color:'var(--rose)',
                    fontWeight:700, letterSpacing:'.08em', textTransform:'uppercase', marginBottom:4 }}>
                    Biggest Gap
                  </div>
                  <div style={{ fontSize:13, fontWeight:600, color:'var(--text)' }}>
                    {data.biggestGap}
                  </div>
                </div>
              </div>

              <p style={{ fontSize:13, color:'var(--text-soft)',
                lineHeight:1.65, margin:0 }}>{data.summary}</p>
            </div>
          </div>

          {/* Dimension bars */}
          <div style={{ display:'flex', flexDirection:'column', gap:16,
            background:'rgba(255,255,255,0.02)',
            border:'1px solid var(--glass-border)',
            borderRadius:'var(--radius-lg)', padding:20 }}>
            {data.dimensions.map((d,i) => <DimBar key={d.name} dim={d} index={i}/>)}
          </div>

          {/* Re-analyse button */}
          <button onClick={analyse} disabled={loading}
            style={{ alignSelf:'flex-start' }} className="btn-ghost">
            ↻ Re-analyse
          </button>
        </div>
      )}
    </div>
  )
}
