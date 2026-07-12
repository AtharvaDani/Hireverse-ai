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
 * Resume Score Agent.
 *
 * Produces a single 0-100 overall score plus a breakdown across a few
 * fixed dimensions (clarity, impact/quantification, ATS-friendliness,
 * structure). Kept as plain formatted text per the chosen design (no
 * charts/gauges), but with a strict, consistent format so the same
 * resume re-scored later is comparable - useful for tracking improvement
 * across edits, even without persisting score history yet.
 */
@Service
public class ScoreAgent extends BaseAgent {

    private static final String SYSTEM_PROMPT_TEMPLATE = """
            You are a resume scoring tool acting as the "Score Agent" inside
            a multi-agent career assistant system.

            You have been given excerpts from the candidate's resume below.
            Score it honestly based ONLY on this content - a generic,
            unquantified resume should score low on impact even if it's
            well-formatted; don't inflate scores to be encouraging.

            --- RESUME CONTEXT START ---
            %s
            --- RESUME CONTEXT END ---

            Respond in EXACTLY this format (keep the headers and structure
            identical every time so scores are comparable across multiple
            runs):

            OVERALL SCORE: <integer 0-100>/100

            BREAKDOWN:
            - Clarity & Readability: <integer 0-25>/25 - <one short sentence why>
            - Impact & Quantification: <integer 0-25>/25 - <one short sentence why>
            - ATS-Friendliness: <integer 0-25>/25 - <one short sentence why>
            - Structure & Completeness: <integer 0-25>/25 - <one short sentence why>

            TOP 3 IMPROVEMENTS:
            1. <most impactful specific change to make>
            2. <second most impactful specific change>
            3. <third most impactful specific change>

            The four breakdown scores must sum to the overall score. Be
            specific in your reasoning - reference actual resume content,
            not generic advice.
            """;

    private final ChatClient chatClient;
    private final VectorStore vectorStore;

    public ScoreAgent(ChatClient.Builder chatClientBuilder, VectorStore vectorStore) {
        this.chatClient = chatClientBuilder.build();
        this.vectorStore = vectorStore;
    }

    @Override
    public AgentType getType() {
        return AgentType.SCORE_AGENT;
    }

    @Override
    public String handle(UUID resumeId, String userMessage, String jobDescription) {
        String ragContext = retrieveResumeContext(resumeId);

        if (ragContext.isBlank()) {
            return "I couldn't find any indexed content for this resume yet. "
                    + "Please make sure the resume finished uploading and indexing before asking questions about it.";
        }

        String systemPrompt = SYSTEM_PROMPT_TEMPLATE.formatted(ragContext);

        String effectiveUserMessage = (userMessage == null || userMessage.isBlank())
                ? "Score my resume."
                : userMessage;

        return callWithRetry(chatClient, systemPrompt, effectiveUserMessage);
    }

    private String retrieveResumeContext(UUID resumeId) {
        FilterExpressionBuilder filterBuilder = new FilterExpressionBuilder();

        // Broad, low-threshold query: scoring needs near-complete coverage
        // of the resume, not just the chunks closest to one narrow topic.
        SearchRequest searchRequest = SearchRequest.builder()
                .query("resume summary experience education skills projects")
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

