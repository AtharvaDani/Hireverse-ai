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
 * Resume Rewrite / Optimizer Agent.
 *
 * Takes the candidate's resume content and rewrites the weakest bullet
 * points to be more impactful: stronger action verbs, quantified results
 * where plausible, and tighter wording - shown as clear "before -> after"
 * pairs so the user can see exactly what changed and why.
 *
 * Same RAG pattern as the other agents: pull the resume's indexed chunks,
 * ground the prompt in them, ask Gemini to do the rewriting.
 */
@Service
public class RewriteAgent extends BaseAgent {

    private static final String SYSTEM_PROMPT_TEMPLATE = """
            You are an expert resume writer acting as the "Rewrite Agent"
            inside a multi-agent career assistant system.

            You have been given excerpts from the candidate's resume below.
            Base your rewrites ONLY on this content - do not invent new
            achievements, metrics, or responsibilities that aren't implied
            by what's actually there. If a metric is plausible but not
            stated, you may suggest the candidate add a specific number
            rather than inventing one yourself.

            --- RESUME CONTEXT START ---
            %s
            --- RESUME CONTEXT END ---
            %s

            Pick the 4-6 bullet points or sentences that would benefit most
            from rewriting (weak verbs, vague impact, passive voice, missing
            quantification). For EACH one, respond in this exact format:

            ORIGINAL: <the original bullet point, verbatim>
            REWRITTEN: <your improved version>
            WHY: <one short sentence explaining the specific improvement>

            Leave a blank line between each set. Do not add any other
            commentary, headers, or summary before or after - just the
            ORIGINAL/REWRITTEN/WHY blocks.
            """;

    private final ChatClient chatClient;
    private final VectorStore vectorStore;

    public RewriteAgent(ChatClient.Builder chatClientBuilder, VectorStore vectorStore) {
        this.chatClient = chatClientBuilder.build();
        this.vectorStore = vectorStore;
    }

    @Override
    public AgentType getType() {
        return AgentType.REWRITE_AGENT;
    }

    @Override
    public String handle(UUID resumeId, String userMessage, String jobDescription) {
        String ragContext = retrieveResumeContext(resumeId);

        if (ragContext.isBlank()) {
            return "I couldn't find any indexed content for this resume yet. "
                    + "Please make sure the resume finished uploading and indexing before asking questions about it.";
        }

        String jobDescriptionBlock = (jobDescription == null || jobDescription.isBlank())
                ? ""
                : """

                When rewriting, also lean toward language and emphasis that
                aligns with this target job description:
                --- TARGET JOB DESCRIPTION START ---
                %s
                --- TARGET JOB DESCRIPTION END ---
                """.formatted(jobDescription);

        String systemPrompt = SYSTEM_PROMPT_TEMPLATE.formatted(ragContext, jobDescriptionBlock);

        String effectiveUserMessage = (userMessage == null || userMessage.isBlank())
                ? "Rewrite the weakest bullet points in my resume to be more impactful."
                : userMessage;

        return callWithRetry(chatClient, systemPrompt, effectiveUserMessage);
    }

    private String retrieveResumeContext(UUID resumeId) {
        FilterExpressionBuilder filterBuilder = new FilterExpressionBuilder();

        // Broad query to pull the bulk of the resume's experience/project
        // bullets, since rewriting needs wide coverage, not a narrow match.
        SearchRequest searchRequest = SearchRequest.builder()
                .query("experience projects responsibilities achievements")
                .topK(10)
                .similarityThreshold(0.3)
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

