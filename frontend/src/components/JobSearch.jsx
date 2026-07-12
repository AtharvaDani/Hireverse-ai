import { useState, useEffect } from 'react'
import { searchJobs } from '../services/api'

export default function JobSearch({ resumeId }) {
  const [keywords, setKeywords] = useState('')
  const [location, setLocation] = useState('')
  const [isLoading, setIsLoading] = useState(false)
  const [error, setError] = useState(null)
  const [results, setResults] = useState(null)
  const [hasAutoSearched, setHasAutoSearched] = useState(false)

  async function handleSearch(useAutoDetect) {
    setIsLoading(true)
    setError(null)
    setResults(null)

    try {
      const response = await searchJobs(
        resumeId,
        useAutoDetect ? null : keywords.trim(),
        location.trim() || null,
        null // country defaults to India server-side
      )
      setResults(response)
      if (useAutoDetect) {
        setKeywords(response.searchKeywords)
      }
    } catch (err) {
      const msg = err.response?.data?.error || 'Something went wrong searching for jobs.'
      setError(msg)
    } finally {
      setIsLoading(false)
    }
  }

  // Auto-run an AI-driven search the moment this tab is opened, so the
  // candidate doesn't have to click anything to see results - this is what
  // makes the flow feel "automatic" rather than requiring manual setup.
  useEffect(() => {
    if (!hasAutoSearched) {
      setHasAutoSearched(true)
      handleSearch(true)
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  function formatSalary(min, max) {
    if (!min && !max) return null
    const fmt = (n) => `\u20b9${Math.round(n).toLocaleString('en-IN')}`
    if (min && max) return `${fmt(min)} \u2013 ${fmt(max)} / yr`
    return `${fmt(min || max)} / yr`
  }

  return (
    <div className="job-search-card">
      <h2 className="agent-tab-title">Find matching jobs</h2>
      <p className="agent-tab-description">
        We'll pull real, current job listings and tell you which ones actually fit your résumé —
        or just hit "Auto-detect" and we'll figure out what to search for.
      </p>

      <div className="job-search-controls">
        <input
          type="text"
          value={keywords}
          onChange={(e) => setKeywords(e.target.value)}
          placeholder="Job title or keywords (e.g. java backend developer)"
          className="job-search-input"
          disabled={isLoading}
        />
        <input
          type="text"
          value={location}
          onChange={(e) => setLocation(e.target.value)}
          placeholder="City (optional, e.g. Mumbai)"
          className="job-search-input job-search-input-location"
          disabled={isLoading}
        />
      </div>

      <div className="job-search-buttons">
        <button
          onClick={() => handleSearch(false)}
          disabled={isLoading || !keywords.trim()}
          className="agent-tab-button"
        >
          {isLoading ? 'Searching\u2026' : 'Search'}
        </button>
        <button
          onClick={() => handleSearch(true)}
          disabled={isLoading}
          className="job-search-autodetect-button"
        >
          \u2728 Auto-detect from r\u00e9sum\u00e9
        </button>
      </div>

      {error && <p className="agent-tab-error">{error}</p>}

      {isLoading && (
        <div className="agent-tab-skeleton">
          <div className="skeleton-line" style={{ width: '60%' }} />
          <div className="skeleton-line" style={{ width: '85%' }} />
          <div className="skeleton-line" style={{ width: '70%' }} />
        </div>
      )}

      {results && (
        <div className="job-search-results">
          <div className="job-search-meta">
            Searched for <strong>"{results.searchKeywords}"</strong> &middot; {results.listings.length} result{results.listings.length === 1 ? '' : 's'}
            {results.experienceLevel && (
              <span className={`job-level-badge job-level-${results.experienceLevel.toLowerCase()}`}>
                {results.experienceLevel === 'FRESHER' ? 'Fresher' : 'Experienced'}
              </span>
            )}
          </div>

          {results.fitSummary && (
            <div className="job-fit-summary">
              <div className="bubble-tag">Fit summary</div>
              <p className="job-fit-summary-text">{results.fitSummary}</p>
            </div>
          )}

          <div className="job-listing-list">
            {results.listings.map((job) => (
              <div key={job.id} className="job-listing-card">
                <div className="job-listing-header">
                  <h3 className="job-listing-title">{job.title}</h3>
                  <span className="job-listing-company">{job.companyName}</span>
                </div>

                <div className="job-listing-meta-row">
                  {job.locationDisplayName && (
                    <span className="job-listing-chip">{job.locationDisplayName}</span>
                  )}
                  {formatSalary(job.salaryMin, job.salaryMax) && (
                    <span className="job-listing-chip job-listing-chip-salary">
                      {formatSalary(job.salaryMin, job.salaryMax)}
                    </span>
                  )}
                </div>

                {job.descriptionSnippet && (
                  <p className="job-listing-snippet">{job.descriptionSnippet}</p>
                )}

                <a
                  href={job.applyUrl}
                  target="_blank"
                  rel="noopener noreferrer"
                  className="job-listing-apply-button"
                >
                  Apply on source site &#8599;
                </a>
              </div>
            ))}
          </div>
        </div>
      )}
    </div>
  )
}
