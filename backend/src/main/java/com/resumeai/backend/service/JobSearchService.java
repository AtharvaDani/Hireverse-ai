package com.resumeai.backend.service;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.resumeai.backend.dto.JobListing;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Thin client for the free Adzuna job search API
 * (https://developer.adzuna.com). No credit card required - sign up for
 * app_id + app_key and set them as ADZUNA_APP_ID / ADZUNA_APP_KEY env vars.
 *
 * Adzuna's API is region-scoped via a country code in the URL path
 * (e.g. "in" for India, "us", "gb"). This service defaults to whatever
 * adzuna.default-country is configured, but callers can override it per
 * request.
 */
@Service
public class JobSearchService {

    private final RestClient restClient;
    private final String appId;
    private final String appKey;
    private final String defaultCountry;

    public JobSearchService(
            @Value("${adzuna.app-id}") String appId,
            @Value("${adzuna.app-key}") String appKey,
           
             @Value("${adzuna.default-country:in}") String defaultCountry
    ) {
        this.restClient = RestClient.create();
        this.appId = appId;
        this.appKey = appKey;
        this.defaultCountry = defaultCountry;
    }

    /**
     * Searches Adzuna for job listings matching the given keywords.
     *
     * @param keywords     free-text search query (e.g. "java backend developer")
     * @param location     optional city/region filter (e.g. "Mumbai"); null/blank for no filter
     * @param countryCode  optional ISO-ish country code Adzuna uses (e.g. "in", "us", "gb");
     *                     falls back to adzuna.default-country if null/blank
     * @param resultsLimit how many listings to fetch (Adzuna caps at 50 per page)
     */
    public List<JobListing> search(String keywords, String location, String countryCode, int resultsLimit) {
        if (appId == null || appId.isBlank() || appKey == null || appKey.isBlank()) {
            throw new IllegalStateException(
                    "Adzuna API credentials are not configured. Set ADZUNA_APP_ID and ADZUNA_APP_KEY " +
                            "(sign up free at https://developer.adzuna.com/signup) to enable job search."
            );
        }

        String country = Optional.ofNullable(countryCode)
                .filter(c -> !c.isBlank())
                .orElse(defaultCountry);

        String url = UriComponentsBuilder
                .fromUriString("https://api.adzuna.com/v1/api/jobs/{country}/search/1")
                .queryParam("app_id", appId)
                .queryParam("app_key", appKey)
                .queryParam("what", keywords)
                .queryParam("results_per_page", Math.min(resultsLimit, 50))
                .queryParam("content-type", "application/json")
                .queryParamIfPresent("where", Optional.ofNullable(location).filter(l -> !l.isBlank()))
                .buildAndExpand(Map.of("country", country))
                .toUri()
                .toString();

        AdzunaSearchResponse response = restClient.get()
                .uri(url)
                .retrieve()
                .body(AdzunaSearchResponse.class);

        if (response == null || response.results() == null) {
            return List.of();
        }

        return response.results().stream()
                .map(this::toJobListing)
                .toList();
    }

    private JobListing toJobListing(AdzunaJob job) {
        return new JobListing(
                job.id(),
                job.title(),
                job.company() != null ? job.company().displayName() : "Unknown company",
                job.location() != null ? job.location().displayName() : null,
                truncate(job.description(), 400),
                job.salaryMin(),
                job.salaryMax(),
                job.redirectUrl(),
                job.created()
        );
    }

    private String truncate(String text, int maxLength) {
        if (text == null) return null;
        return text.length() <= maxLength ? text : text.substring(0, maxLength) + "…";
    }

    // ---- Adzuna raw response shape (only the fields we actually use) ----

    private record AdzunaSearchResponse(List<AdzunaJob> results) {
    }

    private record AdzunaJob(
            String id,
            String title,
            String description,
            String created,
            @JsonProperty("redirect_url") String redirectUrl,
            @JsonProperty("salary_min") Double salaryMin,
            @JsonProperty("salary_max") Double salaryMax,
            AdzunaCompany company,
            AdzunaLocation location
    ) {
    }

    private record AdzunaCompany(@JsonProperty("display_name") String displayName) {
    }

    private record AdzunaLocation(@JsonProperty("display_name") String displayName) {
    }
}
