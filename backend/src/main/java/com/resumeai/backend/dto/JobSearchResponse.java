package com.resumeai.backend.dto;

import java.util.List;

/**
 * Returned by the job search endpoint: the raw listings found, plus an
 * AI-generated summary ranking/commenting on how well they fit the resume.
 */
public record JobSearchResponse(
        String searchKeywords,
        String experienceLevel,
        List<JobListing> listings,
        String fitSummary
) {
}
