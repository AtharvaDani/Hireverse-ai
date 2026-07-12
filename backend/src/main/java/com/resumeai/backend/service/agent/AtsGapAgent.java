package com.resumeai.backend.service.agent;

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
 * ATS Keyword Gap Checker Agent.
 *
 * Unlike JobAgent's fuzzy "how well do I match" critique, this agent does
 * a narrower, more mechanical job: extract concrete keywords/skills/tools
 * from a job description, and flag exactly which ones do NOT appear
 * (verbatim or as a close variant) anywhere in the resume content. This
 * mirrors what real ATS (applicant tracking system) keyword scanners do,
 * so the output is meant to be a checklist, not a narrative critique.
 */
@Service
public class AtsGapAgent extends BaseAgent {

    private static final String SYSTEM_PROMPT_TEMPLATE = """
            You are an ATS (Applicant Tracking System) keyword analysis tool
            acting as the "ATS Gap Agent" inside a multi-agent career
            assistant system.

            You have been given resume excerpts and a job description below.
            Your job is mechanical and literal, not a holistic critique:
            extract the concrete keywords, skills, tools, certifications,
            and qualifications mentioned in the job description, then check
            whether each one appears in the resume excerpts (verbatim or as
            an obvious variant/synonym, e.g. "JS" matches "JavaScript").

            --- RESUME CONTEXT START ---
            %s
            --- RESUME CONTEXT END ---

            --- JOB DESCRIPTION START ---
            %s
            --- JOB DESCRIPTION END ---

            Respond in exactly this structure:

            MATCHED KEYWORDS:
            - <keyword> (found in resume)
            - ... (list every keyword from the job description that IS
              present in the resume, even as a close variant)

            MISSING KEYWORDS:
            - <keyword> - <one short note on where/how to plausibly add it,
              e.g. "mentioned nowhere; consider adding if you have this
              experience">
            - ... (list every keyword from the job description that is NOT
              present anywhere in the resume excerpts)

            RECOMMENDATION:
            <One short paragraph: if there are many missing keywords that
            ARE genuinely part of the candidate's skill set based on the
            resume context, suggest adding them explicitly. If a missing
            keyword does NOT seem to match the candidate's actual
            background, say so honestly rather than suggesting they
            fabricate experience.>

            Be literal and exhaustive about keyword extraction - don't skip
            keywords just because they seem minor (tools, certifications,
            soft skills mentioned explicitly all count).
            """;

    private final ChatClient chatClient;
    private final VectorStore vectorStore;

    public AtsGapAgent(ChatClient.Builder chatClientBuilder, VectorStore vectorStore) {
        this.chatClient = chatClientBuilder.build();
        this.vectorStore = vectorStore;
    }

    @Override
    public AgentType getType() {
        return AgentType.ATS_GAP_AGENT;
    }

    @Override
    public String handle(UUID resumeId, String userMessage, String jobDescription) {
        if (jobDescription == null || jobDescription.isBlank()) {
            return "To check for ATS keyword gaps, please paste a job description "
                    + "in the job description field first.";
        }

        String ragContext = retrieveResumeContext(resumeId, jobDescription);

        if (ragContext.isBlank()) {
            return "I couldn't find any indexed content for this resume yet. "
                    + "Please make sure the resume finished uploading and indexing before asking questions about it.";
        }

        String systemPrompt = SYSTEM_PROMPT_TEMPLATE.formatted(ragContext, jobDescription);

        String effectiveUserMessage = (userMessage == null || userMessage.isBlank())
                ? "Check my resume for missing ATS keywords against this job description."
                : userMessage;

        return callWithRetry(chatClient, systemPrompt, effectiveUserMessage);
    }

    private String retrieveResumeContext(UUID resumeId, String jobDescription) {
        FilterExpressionBuilder filterBuilder = new FilterExpressionBuilder();

        // Pull broadly + bias toward the job description's content, since
        // we need near-complete resume coverage to confidently say a
        // keyword is "missing" rather than just "not retrieved".
        SearchRequest searchRequest = SearchRequest.builder()
                .query(jobDescription)
                .topK(12)
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
}

