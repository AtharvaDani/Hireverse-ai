package com.resumeai.backend.dto;

/**
 * A single job listing, normalized from Adzuna's API response into the
 * subset of fields the frontend actually needs.
 */
public record JobListing(
        String id,
        String title,
        String companyName,
        String locationDisplayName,
        String descriptionSnippet,
        Double salaryMin,
        Double salaryMax,
        String applyUrl,
        String createdDate
) {
}
