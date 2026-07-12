package com.resumeai.backend.service.agent;

import com.resumeai.backend.dto.JobListing;
import com.resumeai.backend.dto.JobSearchResponse;
import com.resumeai.backend.service.JobSearchService;
import com.resumeai.backend.service.ResumeIndexingService;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Job Search Agent.
 *
 * IMPORTANT: this is a plain @Service, NOT an implementation of the Agent
 * interface. It is called directly by JobSearchController, not routed
 * through SupervisorAgentService. Do not make this implement Agent and
 * return an existing AgentType (e.g. RESUME_AGENT) - that causes a
 * duplicate-key crash in SupervisorAgentService's agentsByType map at
 * startup, since two beans would claim the same AgentType.
 *
 * Flow:
 *   1. If the caller didn't supply explicit search keywords, pull the
 *      resume's RAG context and ask Gemini to extract a good search query
 *      (role title + top skills) AND an experience level (fresher vs
 *      experienced, based on years of experience visible in the resume).
 *   2. Call Adzuna (free job search API) with those keywords + location.
 *   3. Ask Gemini to summarize how well the returned listings fit the
 *      resume - which ones are worth applying to and why.
 */
@Service
public class JobSearchAgent {

    private static final String PROFILE_EXTRACTION_PROMPT = """
            Based on the resume excerpts below, respond with EXACTLY two
            lines and nothing else:

            QUERY: <a 2-5 word job search query - role title plus top
            skills, e.g. "java backend developer spring boot">
            LEVEL: <either FRESHER (less than 2 years of professional
            experience visible in the resume) or EXPERIENCED (2+ years)>

            --- RESUME CONTEXT ---
            %s
            """;

    private static final String FIT_SUMMARY_PROMPT_TEMPLATE = """
            You are a career coach acting as the "Job Search Agent" inside a
            multi-agent career assistant system. A search for "%s" returned
            the job listings below. Based on the candidate's resume excerpts
            (experience level: %s), briefly assess the overall batch of
            results.

            --- RESUME CONTEXT ---
            %s

            --- JOB LISTINGS (title, company, snippet) ---
            %s

            In 3-5 short sentences: which 1-2 listings look like the
            strongest fit and why (reference specific resume content), and
            whether the candidate should adjust their search terms to get
            better results. If a listing looks mismatched for this
            candidate's experience level (e.g. asking for senior experience
            from a fresher), say so plainly. Be specific, not generic.
            """;

    private final ChatClient chatClient;
    private final VectorStore vectorStore;
    private final JobSearchService jobSearchService;

    public JobSearchAgent(ChatClient.Builder chatClientBuilder, VectorStore vectorStore, JobSearchService jobSearchService) {
        this.chatClient = chatClientBuilder.build();
        this.vectorStore = vectorStore;
        this.jobSearchService = jobSearchService;
    }

    public JobSearchResponse search(UUID resumeId, String keywords, String location, String country) {
        String ragContext = retrieveResumeContext(resumeId);

        String effectiveKeywords = keywords;
        String experienceLevel = null;

        if (effectiveKeywords == null || effectiveKeywords.isBlank()) {
            ExtractedProfile profile = extractProfileFromResume(ragContext);
            effectiveKeywords = profile.query();
            experienceLevel = profile.level();
        }

        List<JobListing> listings = jobSearchService.search(effectiveKeywords, location, country, 15);

        String fitSummary = listings.isEmpty()
                ? "No listings were found for this search. Try broader or different keywords."
                : summarizeFit(effectiveKeywords, experienceLevel, ragContext, listings);

        return new JobSearchResponse(effectiveKeywords, experienceLevel, listings, fitSummary);
    }

    private ExtractedProfile extractProfileFromResume(String ragContext) {
        if (ragContext.isBlank()) {
            return new ExtractedProfile("software developer", "FRESHER");
        }

        String prompt = PROFILE_EXTRACTION_PROMPT.formatted(ragContext);
        String result = callWithRetry(prompt);

        String query = "software developer";
        String level = "FRESHER";

        if (result != null) {
            for (String line : result.split("\n")) {
                String trimmed = line.trim();
                if (trimmed.toUpperCase().startsWith("QUERY:")) {
                    query = trimmed.substring(trimmed.indexOf(':') + 1).trim();
                } else if (trimmed.toUpperCase().startsWith("LEVEL:")) {
                    String rawLevel = trimmed.substring(trimmed.indexOf(':') + 1).trim().toUpperCase();
                    level = rawLevel.contains("EXPERIENCED") ? "EXPERIENCED" : "FRESHER";
                }
            }
        }

        return new ExtractedProfile(query.isBlank() ? "software developer" : query, level);
    }

    private String summarizeFit(String keywords, String experienceLevel, String ragContext, List<JobListing> listings) {
        String listingsText = listings.stream()
                .map(job -> "- %s at %s: %s".formatted(
                        job.title(), job.companyName(),
                        job.descriptionSnippet() != null ? job.descriptionSnippet() : ""
                ))
                .collect(Collectors.joining("\n"));

        String prompt = FIT_SUMMARY_PROMPT_TEMPLATE.formatted(
                keywords,
                experienceLevel != null ? experienceLevel : "unknown",
                ragContext.isBlank() ? "(no resume context available)" : ragContext,
                listingsText
        );

        return callWithRetry(prompt);
    }

    private String retrieveResumeContext(UUID resumeId) {
        FilterExpressionBuilder filterBuilder = new FilterExpressionBuilder();

        SearchRequest searchRequest = SearchRequest.builder()
                .query("skills experience role job title years")
                .topK(8)
                .similarityThreshold(0.2)
                .filterExpression(
                        filterBuilder.eq(ResumeIndexingService.RESUME_ID_METADATA_KEY, resumeId.toString()).build()
                )
                .build();

        List<Document> results = vectorStore.similaritySearch(searchRequest);

        return results.stream()
                .map(Document::getText)
                .collect(Collectors.joining("\n---\n"));
    }

    private record ExtractedProfile(String query, String level) {
    }

    // ── Gemini rate-limit retry ───────────────────────────────────────────
    private String callWithRetry(String prompt) {
        Exception last = null;
        long wait = 16_000L;
        for (int attempt = 1; attempt <= 3; attempt++) {
            try {
                return chatClient.prompt().user(prompt).call().content();
            } catch (Exception e) {
                String msg = "";
                Throwable t = e;
                while (t != null) { if (t.getMessage() != null) msg += t.getMessage(); t = t.getCause(); }
                if (!msg.contains("429") && !msg.toLowerCase().contains("quota") && !msg.toLowerCase().contains("exhausted")) throw e;
                last = e;
                System.out.printf("[JobSearchAgent] 429 (attempt %d/3) waiting %ds%n", attempt, wait/1000);
                try { Thread.sleep(wait); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); throw new RuntimeException(ie); }
                wait = Math.min(wait * 2, 60_000L);
            }
        }
        throw new RuntimeException("Gemini rate limit persists. Wait ~1 minute.", last);
    }

}

