import axios from 'axios'

const API_BASE_URL = import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080/api'

const api = axios.create({
  baseURL: API_BASE_URL,
})

/**
 * Step 2 in the architecture: "Send PDF + User Request" (upload part).
 * Uploads a resume PDF; backend stores it, extracts text, and indexes
 * it into the vector knowledge base before returning.
 *
 * @param {File} file - the PDF File object from an <input type="file">
 * @returns {Promise<{resumeId: string, fileName: string, indexed: boolean, message: string}>}
 */
export async function uploadResume(file) {
  const formData = new FormData()
  formData.append('file', file)

  const response = await api.post('/resumes/upload', formData, {
    headers: { 'Content-Type': 'multipart/form-data' },
  })

  return response.data
}

/**
 * Steps 6-8 in the architecture: send a question/request about a specific
 * resume; the Supervisor routes it to an agent, which generates a response
 * grounded in that resume's RAG context.
 *
 * @param {string} resumeId
 * @param {string} message
 * @param {string} [jobDescription] - optional, used for job-matching/interview prep
 * @returns {Promise<{agentUsed: string, response: string}>}
 */
export async function askAgent(resumeId, message, jobDescription) {
  const response = await api.post(`/resumes/${resumeId}/chat`, {
    resumeId,
    message,
    jobDescription: jobDescription || null,
  })
  return response.data
}

/**
 * Calls a specific agent directly by name (Resume Score, ATS Gap Checker,
 * Rewrite Optimizer tabs), bypassing the Supervisor's routing decision.
 *
 * @param {string} resumeId
 * @param {string} agentType - one of 'SCORE_AGENT', 'ATS_GAP_AGENT', 'REWRITE_AGENT'
 * @param {string} [message] - optional, agent has a sensible default if blank
 * @param {string} [jobDescription] - required for ATS_GAP_AGENT, optional for REWRITE_AGENT
 * @returns {Promise<{agentUsed: string, response: string}>}
 */
export async function callAgentDirectly(resumeId, agentType, message, jobDescription) {
  const response = await api.post(`/resumes/${resumeId}/agent`, {
    agentType,
    message: message || null,
    jobDescription: jobDescription || null,
  })
  return response.data
}

/**
 * Submits a recorded mock interview answer (audio + sampled video frames)
 * for critique. Sent as multipart/form-data since it includes binary blobs.
 *
 * @param {string} resumeId
 * @param {string} question - the interview question that was being answered
 * @param {Blob} audioBlob - recorded audio (e.g. audio/webm from MediaRecorder)
 * @param {Blob[]} frameBlobs - JPEG snapshots sampled from the video during recording
 * @returns {Promise<{critique: string}>}
 */
export async function submitMockInterview(resumeId, question, audioBlob, frameBlobs) {
  const formData = new FormData()
  formData.append('question', question)
  formData.append('audio', audioBlob, 'answer.webm')
  frameBlobs.forEach((blob, idx) => {
    formData.append('frames', blob, `frame-${idx}.jpg`)
  })

  const response = await api.post(`/resumes/${resumeId}/mock-interview`, formData, {
    headers: { 'Content-Type': 'multipart/form-data' },
  })
  return response.data
}

/**
 * Searches for real job listings matching the resume (or explicit keywords).
 * If keywords/location are omitted, the backend auto-extracts a search
 * query and experience level from the resume itself.
 *
 * @param {string} resumeId
 * @param {string} [keywords] - optional manual search query
 * @param {string} [location] - optional city/region filter
 * @param {string} [country] - optional Adzuna country code, defaults to 'in' server-side
 * @returns {Promise<{searchKeywords: string, listings: Array, fitSummary: string}>}
 */
export async function searchJobs(resumeId, keywords, location, country) {
  const response = await api.post(`/resumes/${resumeId}/job-search`, {
    keywords: keywords || null,
    location: location || null,
    country: country || null,
  })
  return response.data
}

export default api
