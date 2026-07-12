import { useState, useRef, useCallback, useEffect } from 'react'
import { submitMockInterview } from '../services/api'

const SAMPLE_INTERVIEW_QUESTIONS = [
  'Tell me about a challenging project you worked on and how you handled it.',
  'Walk me through one of the projects on your resume in detail.',
  'Why do you want this role, and why are you a good fit?',
  'Describe a time you disagreed with a teammate. How did you resolve it?',
]

const FRAME_INTERVAL_MS = 2000 // sample one frame every 2 seconds
const MAX_FRAMES = 8 // mirrors backend MAX_FRAMES cap

export default function MockInterview({ resumeId }) {
  const [question, setQuestion] = useState(SAMPLE_INTERVIEW_QUESTIONS[0])
  const [customQuestion, setCustomQuestion] = useState('')
  const [permissionState, setPermissionState] = useState('idle') // idle | requesting | granted | denied
  const [isRecording, setIsRecording] = useState(false)
  const [isSubmitting, setIsSubmitting] = useState(false)
  const [critique, setCritique] = useState(null)
  const [error, setError] = useState(null)
  const [elapsedSeconds, setElapsedSeconds] = useState(0)

  const videoRef = useRef(null)
  const streamRef = useRef(null)
  const mediaRecorderRef = useRef(null)
  const audioChunksRef = useRef([])
  const frameBlobsRef = useRef([])
  const frameIntervalRef = useRef(null)
  const timerIntervalRef = useRef(null)
  const canvasRef = useRef(null)
  const audioMimeTypeRef = useRef('audio/webm')

  const activeQuestion = customQuestion.trim() || question

  // Release the camera/mic and clear any running intervals when the user
  // navigates away from this tab - otherwise the camera indicator light
  // stays on and the recorder keeps a dangling reference to the stream.
  useEffect(() => {
    return () => {
      clearInterval(frameIntervalRef.current)
      clearInterval(timerIntervalRef.current)
      if (mediaRecorderRef.current && mediaRecorderRef.current.state !== 'inactive') {
        mediaRecorderRef.current.stop()
      }
      if (streamRef.current) {
        streamRef.current.getTracks().forEach((track) => track.stop())
      }
    }
  }, [])

  async function requestCameraAccess() {
    setPermissionState('requesting')
    setError(null)
    try {
      const stream = await navigator.mediaDevices.getUserMedia({ video: true, audio: true })
      streamRef.current = stream
      if (videoRef.current) {
        videoRef.current.srcObject = stream
      }
      setPermissionState('granted')
    } catch (err) {
      setPermissionState('denied')
      setError('Camera/microphone access was denied or unavailable. Please allow access and try again.')
    }
  }

  function captureFrame() {
    if (!videoRef.current || !canvasRef.current) return
    const canvas = canvasRef.current
    const video = videoRef.current
    canvas.width = video.videoWidth || 320
    canvas.height = video.videoHeight || 240
    const ctx = canvas.getContext('2d')
    ctx.drawImage(video, 0, 0, canvas.width, canvas.height)
    canvas.toBlob((blob) => {
      if (blob && frameBlobsRef.current.length < MAX_FRAMES) {
        frameBlobsRef.current.push(blob)
      }
    }, 'image/jpeg', 0.8)
  }

  function startRecording() {
    if (!streamRef.current) {
      setError('Camera/mic isn\u2019t ready yet. Please enable access first.')
      return
    }

    setCritique(null)
    setError(null)
    audioChunksRef.current = []
    frameBlobsRef.current = []
    setElapsedSeconds(0)

    try {
      // Deliberately NOT passing a mimeType here. Forcing a specific
      // codec string (e.g. 'audio/webm;codecs=opus') is known to throw
      // "There was an error starting the MediaRecorder" on some Chromium
      // builds even when isTypeSupported() reports it as supported. Letting
      // the browser pick its own default and reading back recorder.mimeType
      // afterward is the reliable approach.
      const recorder = new MediaRecorder(streamRef.current)
      mediaRecorderRef.current = recorder
      audioMimeTypeRef.current = recorder.mimeType || 'audio/webm'

      recorder.ondataavailable = (e) => {
        if (e.data.size > 0) audioChunksRef.current.push(e.data)
      }

      recorder.onerror = (e) => {
        setError(`Recording error: ${e.error?.message || 'unknown error'}`)
        setIsRecording(false)
      }

      recorder.start()
      audioMimeTypeRef.current = recorder.mimeType || audioMimeTypeRef.current
      setIsRecording(true)

      // Sample a frame immediately, then on an interval
      captureFrame()
      frameIntervalRef.current = setInterval(captureFrame, FRAME_INTERVAL_MS)
      timerIntervalRef.current = setInterval(() => setElapsedSeconds((s) => s + 1), 1000)
    } catch (err) {
      setError(`Couldn\u2019t start recording: ${err.message || 'your browser may not support this.'}`)
    }
  }

  const stopRecordingAndSubmit = useCallback(async () => {
    const recorder = mediaRecorderRef.current
    if (!recorder) return

    clearInterval(frameIntervalRef.current)
    clearInterval(timerIntervalRef.current)

    const stopped = new Promise((resolve) => {
      recorder.onstop = resolve
    })
    recorder.stop()
    await stopped

    setIsRecording(false)
    setIsSubmitting(true)
    setError(null)

    try {
      const audioBlob = new Blob(audioChunksRef.current, { type: audioMimeTypeRef.current })
      const result = await submitMockInterview(resumeId, activeQuestion, audioBlob, frameBlobsRef.current)
      setCritique(result.critique)
    } catch (err) {
      const msg = err.response?.data?.error || 'Something went wrong analyzing your answer.'
      setError(msg)
    } finally {
      setIsSubmitting(false)
    }
  }, [resumeId, activeQuestion])

  function formatTime(totalSeconds) {
    const m = Math.floor(totalSeconds / 60)
    const s = totalSeconds % 60
    return `${m}:${s.toString().padStart(2, '0')}`
  }

  return (
    <div className="mock-interview-card">
      <h2 className="agent-tab-title">Mock interview</h2>
      <p className="agent-tab-description">
        Pick or write a question, record yourself answering it on camera, and get feedback on
        your content, pacing, and presentation — grounded in your résumé.
      </p>

      <div className="mi-question-picker">
        <label className="mi-label">Question</label>
        <select
          value={question}
          onChange={(e) => setQuestion(e.target.value)}
          className="mi-select"
          disabled={isRecording || isSubmitting}
        >
          {SAMPLE_INTERVIEW_QUESTIONS.map((q) => (
            <option key={q} value={q}>{q}</option>
          ))}
        </select>
        <input
          type="text"
          value={customQuestion}
          onChange={(e) => setCustomQuestion(e.target.value)}
          placeholder="...or write your own question"
          className="mi-custom-input"
          disabled={isRecording || isSubmitting}
        />
      </div>

      <div className="mi-stage">
        <video ref={videoRef} autoPlay muted playsInline className="mi-video" />
        <canvas ref={canvasRef} style={{ display: 'none' }} />

        {permissionState !== 'granted' && (
          <div className="mi-stage-overlay">
            <button
              onClick={requestCameraAccess}
              disabled={permissionState === 'requesting'}
              className="mi-enable-button"
            >
              {permissionState === 'requesting' ? 'Requesting access…' : '🎥 Enable camera & mic'}
            </button>
          </div>
        )}

        {isRecording && (
          <div className="mi-recording-badge">
            <span className="mi-recording-dot" />
            REC {formatTime(elapsedSeconds)}
          </div>
        )}
      </div>

      <div className="mi-controls">
        {permissionState === 'granted' && !isRecording && !isSubmitting && (
          <button onClick={startRecording} className="agent-tab-button">
            ● Start recording answer
          </button>
        )}

        {isRecording && (
          <button onClick={stopRecordingAndSubmit} className="mi-stop-button">
            ■ Stop &amp; analyze
          </button>
        )}

        {isSubmitting && (
          <div className="mi-analyzing">
            <span className="spinner spinner-dark" />
            Transcribing audio and analyzing your delivery…
          </div>
        )}
      </div>

      {error && <p className="agent-tab-error">{error}</p>}

      {critique && (
        <div className="agent-tab-result">
          <p className="agent-tab-result-text">{critique}</p>
        </div>
      )}
    </div>
  )
}
