import { useState } from 'react'
import { callAgentDirectly } from '../services/api'

function extractScore(text) {
  const m = text?.match(/OVERALL SCORE:\s*(\d{1,3})\s*\/\s*100/i)
  return m ? Math.min(100, parseInt(m[1],10)) : null
}
function extractBreakdown(text) {
  if (!text) return []
  const lines = text.match(/^- ([^:]+):\s*(\d{1,2})\/(\d{1,2})/gm)
  if (!lines) return []
  return lines.map(l => {
    const m = l.match(/^- ([^:]+):\s*(\d{1,2})\/(\d{1,2})/)
    return { label: m[1].trim(), value: parseInt(m[2],10), max: parseInt(m[3],10) }
  })
}

const CHIP_COLORS = ['chip-gold','chip-clay','chip-teal','chip-plum']

function ScoreGauge({ score }) {
  const r = 56, c = 2*Math.PI*r
  const offset = c - (score/100)*c
  const color = score>=75 ? 'var(--emerald)' : score>=50 ? 'var(--amber)' : 'var(--rose)'
  return (
    <div className="score-gauge">
      <svg width="140" height="140" viewBox="0 0 140 140">
        <circle cx="70" cy="70" r={r} fill="none" stroke="rgba(255,255,255,0.08)" strokeWidth="10"/>
        <circle cx="70" cy="70" r={r} fill="none" stroke={color} strokeWidth="10"
          strokeLinecap="round" strokeDasharray={c} strokeDashoffset={offset}
          transform="rotate(-90 70 70)" className="score-gauge-arc"
          style={{ filter:`drop-shadow(0 0 12px ${color})` }}/>
      </svg>
      <div className="score-gauge-number">{score}</div>
      <div className="score-gauge-label" style={{ color }}>
        {score>=85?'Excellent':score>=70?'Strong':score>=50?'Needs work':'Rewrite needed'}
      </div>
    </div>
  )
}

export default function AgentTab({ resumeId, agentType, title, description, buttonLabel,
  needsJobDescription=false, jobDescriptionRequired=false, variant }) {
  const [jobDesc,  setJobDesc]  = useState('')
  const [result,   setResult]   = useState(null)
  const [loading,  setLoading]  = useState(false)
  const [error,    setError]    = useState(null)

  async function run() {
    if (jobDescriptionRequired && !jobDesc.trim()) { setError('Paste a job description first.'); return }
    setLoading(true); setError(null); setResult(null)
    try {
      const r = await callAgentDirectly(resumeId, agentType, null, jobDesc.trim())
      setResult(r.response)
    } catch (e) {
      const raw = e.response?.data?.error || e.message || 'Something went wrong.'
      setError(raw.includes('429') || raw.toLowerCase().includes('quota')
        ? '⚠️ Rate limit reached. Please wait 60 seconds and try again.'
        : raw)
    } finally { setLoading(false) }
  }

  const score     = variant==='score' ? extractScore(result)     : null
  const breakdown = variant==='score' ? extractBreakdown(result) : []

  return (
    <div className="agent-tab-card">
      <h2 className="agent-tab-title">{title}</h2>
      <p className="agent-tab-description">{description}</p>

      {needsJobDescription && (
        <textarea value={jobDesc} onChange={e => setJobDesc(e.target.value)}
          placeholder={jobDescriptionRequired
            ? 'Paste the job description here (required)…'
            : 'Paste a job description to tailor results (optional)…'}
          className="agent-tab-textarea" disabled={loading}/>
      )}

      <button onClick={run} disabled={loading} className="agent-tab-button">
        {loading ? <><span className="spinner"/> Analysing…</> : buttonLabel}
      </button>

      {error && <div className="agent-tab-error">{error}</div>}

      {loading && (
        <div className="agent-tab-skeleton">
          {[70,92,55,80,40].map((w,i) => (
            <div key={i} className="skeleton-line" style={{ width:`${w}%` }}/>
          ))}
        </div>
      )}

      {result && (
        <div className="agent-tab-result">
          {score !== null && <ScoreGauge score={score}/>}
          {breakdown.length > 0 && (
            <div className="breakdown-row">
              {breakdown.map((item,i) => (
                <div key={item.label} className={`breakdown-chip ${CHIP_COLORS[i%4]}`}>
                  <span className="breakdown-chip-num">{item.value}</span>
                  <span className="breakdown-chip-label">{item.label.split(' ')[0]}</span>
                </div>
              ))}
            </div>
          )}
          <p className="agent-tab-result-text">{result}</p>
        </div>
      )}
    </div>
  )
}
