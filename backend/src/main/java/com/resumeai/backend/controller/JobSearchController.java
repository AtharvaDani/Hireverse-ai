package com.resumeai.backend.controller;

import com.resumeai.backend.dto.JobSearchRequest;
import com.resumeai.backend.dto.JobSearchResponse;
import com.resumeai.backend.service.ResumeService;
import com.resumeai.backend.service.agent.JobSearchAgent;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * Job search endpoint. Separate from ResumeController/MockInterviewController
 * since this returns a different response shape (job listings + fit summary,
 * not an agent chat response).
 *
 * If keywords/location aren't provided in the request, the search is fully
 * automatic: JobSearchAgent extracts a query + experience level from the
 * resume itself via Gemini before calling Adzuna.
 */
@RestController
@RequestMapping("/api/resumes")
@RequiredArgsConstructor
public class JobSearchController {

    private final ResumeService resumeService;
    private final JobSearchAgent jobSearchAgent;

    @PostMapping("/{resumeId}/job-search")
    public JobSearchResponse search(
            @PathVariable String resumeId,
            @RequestBody(required = false) JobSearchRequest request
    ) {
        UUID id = UUID.fromString(resumeId);
        resumeService.getResumeOrThrow(id);

        String keywords = request != null ? request.keywords() : null;
        String location = request != null ? request.location() : null;
        String country = request != null ? request.country() : null;

        return jobSearchAgent.search(id, keywords, location, country);
    }
}
