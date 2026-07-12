import { useState, useRef } from 'react'
import { uploadResume } from '../services/api'

export default function ResumeUpload({ onUploadSuccess }) {
  const [file,       setFile]       = useState(null)
  const [uploading,  setUploading]  = useState(false)
  const [error,      setError]      = useState(null)
  const [dragging,   setDragging]   = useState(false)
  const inputRef = useRef(null)

  function validate(f) {
    setError(null)
    if (!f) return
    if (f.type !== 'application/pdf') { setError('Please choose a PDF file.'); return }
    setFile(f)
  }

  async function upload() {
    if (!file) { setError('Choose a PDF résumé first.'); return }
    setUploading(true); setError(null)
    try {
      const result = await uploadResume(file)
      onUploadSuccess(result)
    } catch (e) {
      setError(e.response?.data?.error || 'Upload failed. Please try again.')
    } finally { setUploading(false) }
  }

  return (
    <>
      <div className="upload-card-label">Upload your résumé</div>

      <div
        className={`dropzone ${dragging ? 'is-dragging':''} ${file ? 'has-file':''}`}
        onDragOver={e => { e.preventDefault(); setDragging(true) }}
        onDragLeave={() => setDragging(false)}
        onDrop={e => { e.preventDefault(); setDragging(false); validate(e.dataTransfer.files[0]) }}
        onClick={() => inputRef.current?.click()}
        role="button" tabIndex={0}
        onKeyDown={e => e.key === 'Enter' && inputRef.current?.click()}
      >
        <input ref={inputRef} type="file" accept="application/pdf"
          onChange={e => validate(e.target.files[0])} style={{ display:'none' }} />

        {file ? (
          <>
            <div className="dropzone-icon">📄</div>
            <div className="dropzone-filename">{file.name}</div>
            <div className="dropzone-hint">Click to choose a different file</div>
          </>
        ) : (
          <>
            <div className="dropzone-icon" style={{ fontSize:40 }}>↑</div>
            <div className="dropzone-title">Drop your résumé here</div>
            <div className="dropzone-hint">or click to browse · PDF only</div>
          </>
        )}
      </div>

      {error && <p className="upload-error">{error}</p>}

      <button onClick={upload} disabled={uploading || !file} className="upload-button">
        {uploading
          ? <><span className="spinner"/> Indexing your résumé…</>
          : 'Upload & Analyse →'}
      </button>
    </>
  )
}
