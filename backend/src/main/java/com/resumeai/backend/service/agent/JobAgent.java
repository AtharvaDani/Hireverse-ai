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
 * Fully implemented Job Agent.
 *
 * Flow (mirrors ResumeAgent's pattern):
 *   1. Similarity-search pgvector, scoped to this resume's chunks only.
 *   2. Stuff retrieved resume context + the user-provided job description
 *      into a system prompt.
 *   3. Ask Gemini to compare the two and produce a match analysis:
 *      fit score, matching skills, gaps, and tailoring suggestions.
 *
 * Unlike ResumeAgent, this agent's retrieval query is the job description
 * itself (not the user's chat message) - we want resume chunks that are
 * semantically closest to the role being targeted, not closest to whatever
 * the user happened to type ("compare these please").
 */
@Service
public class JobAgent extends BaseAgent {

    private static final String SYSTEM_PROMPT_TEMPLATE = """
            You are an expert technical recruiter and career coach acting as
            the "Job Agent" inside a multi-agent career assistant system.

            You have been given:
            1. Excerpts retrieved from the candidate's resume.
            2. A job description the candidate wants to match against.

            Base your analysis ONLY on the resume excerpts provided - do not
            invent experience, skills, or qualifications that aren't present.

            --- RESUME CONTEXT START ---
            %s
            --- RESUME CONTEXT END ---

            --- JOB DESCRIPTION START ---
            %s
            --- JOB DESCRIPTION END ---

            When responding, structure your answer with these sections:
            - Overall Fit: a short verdict (e.g. Strong Match / Partial Match /
              Weak Match) with a one-sentence justification.
            - Matching Skills & Experience: specific resume content that
              aligns with the job description, referencing actual items.
            - Gaps: requirements in the job description that the resume
              excerpts do not show evidence of meeting.
            - Suggestions: concrete, actionable ways the candidate could
              tailor their resume or application for this specific role.

            Keep tone honest and constructive, like a recruiter giving real
            feedback rather than generic encouragement.
            """;

    private final ChatClient chatClient;
    private final VectorStore vectorStore;

    public JobAgent(ChatClient.Builder chatClientBuilder, VectorStore vectorStore) {
        this.chatClient = chatClientBuilder.build();
        this.vectorStore = vectorStore;
    }

    @Override
    public AgentType getType() {
        return AgentType.JOB_AGENT;
    }

    @Override
    public String handle(UUID resumeId, String userMessage, String jobDescription) {
        if (jobDescription == null || jobDescription.isBlank()) {
            return "To match your resume against a role, please paste the job "
                    + "description in the job description field before asking.";
        }

        // Retrieve resume chunks most relevant to the JOB DESCRIPTION, not the
        // user's chat message - this surfaces the parts of the resume that
        // actually matter for this specific role.
        String ragContext = retrieveResumeContext(resumeId, jobDescription);

        if (ragContext.isBlank()) {
            return "I couldn't find any indexed content for this resume yet. "
                    + "Please make sure the resume finished uploading and indexing before asking questions about it.";
        }

        String systemPrompt = SYSTEM_PROMPT_TEMPLATE.formatted(ragContext, jobDescription);

        String effectiveUserMessage = (userMessage == null || userMessage.isBlank())
                ? "Compare my resume to this job description and tell me how well I match."
                : userMessage;

        return callWithRetry(chatClient, systemPrompt, effectiveUserMessage);
    }

    private String retrieveResumeContext(UUID resumeId, String query) {
        FilterExpressionBuilder filterBuilder = new FilterExpressionBuilder();

        SearchRequest searchRequest = SearchRequest.builder()
                .query(query)
                .topK(8)
                .similarityThreshold(0.4)
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

