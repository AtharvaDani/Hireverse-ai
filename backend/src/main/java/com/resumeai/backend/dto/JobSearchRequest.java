package com.resumeai.backend.dto;

/**
 * Sent by the frontend's Job Search tab. All fields except resumeId are
 * optional - if keywords/location aren't supplied, the backend derives
 * search keywords from the resume itself via Gemini.
 */
public record JobSearchRequest(
        String keywords,
        String location,
        String country
) {
}
