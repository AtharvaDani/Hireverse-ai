import { useState, useRef, useEffect } from 'react'
import { askAgent } from '../services/api'

const PROMPTS = [
  { label: 'Review my résumé',   text: 'Review my resume and give me detailed feedback' },
  { label: 'Strongest points',   text: 'What are the strongest points of my resume?' },
  { label: 'Match this job',     text: 'How well does my resume match this job description?', needsJob: true },
  { label: 'Mock interview',     text: 'Generate 5 mock interview questions based on my resume.' },
]

function AgentBadge({ name }) {
  const labels = { RESUME_AGENT:'Resume Agent', JOB_AGENT:'Job Agent', INTERVIEW_AGENT:'Interview Agent' }
  return (
    <div className="agent-tag">⬡ {labels[name] || name}</div>
  )
}

export default function Report({ resumeId }) {
  const [message,      setMessage]      = useState('')
  const [jobDesc,      setJobDesc]      = useState('')
  const [showJob,      setShowJob]      = useState(false)
  const [conversation, setConversation] = useState([])
  const [loading,      setLoading]      = useState(false)
  const [error,        setError]        = useState(null)
  const bottomRef = useRef(null)

  useEffect(() => {
    bottomRef.current?.scrollIntoView({ behavior: 'smooth' })
  }, [conversation, loading])

  async function send(textOverride) {
    const text = textOverride ?? message
    if (!text.trim()) return
    setLoading(true); setError(null)
    setConversation(p => [...p, { role:'user', text }])
    setMessage('')
    try {
      const r = await askAgent(resumeId, text, jobDesc.trim())
      setConversation(p => [...p, { role:'agent', text: r.response, agent: r.agentUsed }])
    } catch (e) {
      setError(e.response?.data?.error || 'Something went wrong.')
    } finally { setLoading(false) }
  }

  return (
    <div className="report-card">
      {/* Job description toggle */}
      <div className="job-field">
        <button onClick={() => setShowJob(v => !v)} className="job-toggle" type="button">
          <span className={`job-toggle-caret ${showJob ? 'is-open':''}`}>›</span>
          Job description
          <span className="job-toggle-note">optional, for matching &amp; tailored prep</span>
        </button>
        {showJob && (
          <textarea value={jobDesc} onChange={e => setJobDesc(e.target.value)}
            placeholder="Paste the job description here…"
            className="job-description-textarea" disabled={loading} />
        )}
        {jobDesc.trim() && (
          <p style={{ fontSize:12, color:'var(--emerald)', marginTop:6, display:'flex', alignItems:'center', gap:6 }}>
            <span style={{ width:6, height:6, borderRadius:'50%', background:'var(--emerald)', display:'inline-block' }}/>
            Job description added
          </p>
        )}
      </div>

      {/* Prompt chips */}
      <div className="suggested-prompts">
        {PROMPTS.map(p => (
          <button key={p.label} className="prompt-chip" disabled={loading}
            onClick={() => { if (p.needsJob && !jobDesc.trim()) { setShowJob(true); return } send(p.text) }}>
            {p.label}
          </button>
        ))}
      </div>

      {/* Conversation */}
      <div className="conversation">
        {conversation.length === 0 && !loading && (
          <div style={{ textAlign:'center', padding:'40px 0', color:'var(--text-muted)' }}>
            <div style={{ fontSize:32, marginBottom:12 }}>✦</div>
            <div style={{ fontSize:14 }}>Ask anything about your résumé, or pick a suggestion above.</div>
          </div>
        )}

        {conversation.map((turn, i) => (
          <div key={i} className={`message ${turn.role}`}>
            <div className="message-avatar">
              {turn.role === 'user' ? '👤' : '✦'}
            </div>
            <div>
              {turn.role === 'agent' && turn.agent && <AgentBadge name={turn.agent} />}
              <div className="message-bubble">{turn.text}</div>
            </div>
          </div>
        ))}

        {loading && (
          <div className="message agent">
            <div className="message-avatar">✦</div>
            <div className="message-bubble" style={{ display:'flex', gap:6, alignItems:'center', padding:'14px 18px' }}>
              {[0,1,2].map(i => (
                <span key={i} style={{
                  width:7, height:7, borderRadius:'50%',
                  background:'var(--violet)', display:'inline-block',
                  animation:`bounce .9s ease-in-out ${i*0.15}s infinite`
                }}/>
              ))}
            </div>
          </div>
        )}

        <div ref={bottomRef} />
      </div>

      {error && <div className="agent-tab-error">{error}</div>}

      {/* Input */}
      <div className="chat-input-row">
        <input type="text" value={message}
          onChange={e => setMessage(e.target.value)}
          onKeyDown={e => e.key === 'Enter' && !loading && send()}
          placeholder="Ask something about your résumé…"
          className="chat-input" disabled={loading} />
        <button onClick={() => send()} disabled={loading || !message.trim()} className="send-button">
          {loading ? <span className="spinner"/> : 'Send →'}
        </button>
      </div>

      <style>{`
        @keyframes bounce {
          0%,100% { transform: translateY(0); opacity:.5; }
          50% { transform: translateY(-5px); opacity:1; }
        }
      `}</style>
    </div>
  )
}
