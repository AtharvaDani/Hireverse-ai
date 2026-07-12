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
 * Step 7 in the architecture ("Use Tools + RAG Data") for the Resume Agent
 * specifically. This is the one agent fully implemented in this slice.
 *
 * Flow:
 *   1. Similarity-search the pgvector store, filtered to ONLY this resume's
 *      chunks (via the resumeId metadata tag written during indexing).
 *   2. Stuff the retrieved chunks into a system prompt as grounding context.
 *   3. Ask Gemini (step 8: "Generate Response") to produce a structured
 *      resume critique/report.
 *
 * Job Agent and Interview Agent (see the stub package) follow this exact
 * same shape - only the retrieval query and the prompt content differ.
 */
@Service
public class ResumeAgent extends BaseAgent {

    private static final String SYSTEM_PROMPT_TEMPLATE = """
            You are an expert resume reviewer and career coach acting as the
            "Resume Agent" inside a multi-agent career assistant system.

            You have been given excerpts retrieved from the user's resume below.
            Base your analysis ONLY on this content - do not invent experience,
            skills, or qualifications that are not present in the excerpts.

            --- RESUME CONTEXT START ---
            %s
            --- RESUME CONTEXT END ---

            When responding:
            - Be specific and reference actual content from the resume context.
            - Structure feedback under clear headings such as Strengths,
              Weaknesses, and Suggested Improvements when the user asks for
              a general review.
            - If the user asks something the resume context cannot answer,
              say so plainly rather than guessing.
            - Keep tone constructive and professional, like a senior recruiter
              giving honest, actionable feedback.
            """;

    private final ChatClient chatClient;
    private final VectorStore vectorStore;

    public ResumeAgent(ChatClient.Builder chatClientBuilder, VectorStore vectorStore) {
        this.chatClient = chatClientBuilder.build();
        this.vectorStore = vectorStore;
    }

    @Override
    public AgentType getType() {
        return AgentType.RESUME_AGENT;
    }

    @Override
    public String handle(UUID resumeId, String userMessage, String jobDescription) {
        // jobDescription is irrelevant to a general resume critique, so it's
        // intentionally ignored here - it only matters to JobAgent.
        String ragContext = retrieveResumeContext(resumeId, userMessage);

        if (ragContext.isBlank()) {
            return "I couldn't find any indexed content for this resume yet. "
                    + "Please make sure the resume finished uploading and indexing before asking questions about it.";
        }

        String systemPrompt = SYSTEM_PROMPT_TEMPLATE.formatted(ragContext);

        return callWithRetry(chatClient, systemPrompt, userMessage);
    }

    /**
     * Runs a similarity search against pgvector, scoped to this resume only
     * via a metadata filter expression, and concatenates the matching chunks.
     */
    private String retrieveResumeContext(UUID resumeId, String userMessage) {
        FilterExpressionBuilder filterBuilder = new FilterExpressionBuilder();

        SearchRequest searchRequest = SearchRequest.builder()
                .query(userMessage)
                .topK(5)
                .similarityThreshold(0.5)
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

