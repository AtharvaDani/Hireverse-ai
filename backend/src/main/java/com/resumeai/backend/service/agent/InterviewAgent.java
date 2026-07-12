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
 * Fully implemented Interview Agent.
 *
 * Flow (mirrors ResumeAgent's pattern):
 *   1. Similarity-search pgvector, scoped to this resume's chunks only.
 *   2. Stuff the retrieved resume context (and, if provided, a job
 *      description) into a system prompt.
 *   3. Ask Gemini to generate mock interview questions grounded in the
 *      candidate's actual experience, or to critique a practice answer
 *      if the user's message looks like one.
 *
 * If a job description is supplied (the same optional field used by
 * JobAgent), questions are tailored toward that specific role; otherwise
 * the agent generates general behavioral + technical questions based on
 * what's actually in the resume.
 */
@Service
public class InterviewAgent extends BaseAgent {

    private static final String SYSTEM_PROMPT_TEMPLATE = """
            You are an experienced technical interviewer and career coach
            acting as the "Interview Agent" inside a multi-agent career
            assistant system.

            You have been given excerpts retrieved from the candidate's
            resume below. Base your questions and feedback ONLY on this
            content - do not invent experience, skills, or projects that
            aren't present in the excerpts.

            --- RESUME CONTEXT START ---
            %s
            --- RESUME CONTEXT END ---
            %s

            When responding:
            - If the user asks for interview questions, generate a mix of
              behavioral and technical questions that reference SPECIFIC
              items from the resume (named projects, technologies, roles),
              not generic questions that could apply to anyone.
            - If a job description was provided above, prioritize questions
              relevant to that role's requirements.
            - If the user's message looks like a practice answer to a
              question (rather than a request for new questions), give
              constructive feedback on that answer: clarity, structure
              (e.g. STAR method for behavioral answers), and specificity.
            - Keep tone encouraging but honest, like a mock-interview coach
              preparing a candidate for the real thing.
            """;

    private final ChatClient chatClient;
    private final VectorStore vectorStore;

    public InterviewAgent(ChatClient.Builder chatClientBuilder, VectorStore vectorStore) {
        this.chatClient = chatClientBuilder.build();
        this.vectorStore = vectorStore;
    }

    @Override
    public AgentType getType() {
        return AgentType.INTERVIEW_AGENT;
    }

    @Override
    public String handle(UUID resumeId, String userMessage, String jobDescription) {
        String ragContext = retrieveResumeContext(resumeId, userMessage);

        if (ragContext.isBlank()) {
            return "I couldn't find any indexed content for this resume yet. "
                    + "Please make sure the resume finished uploading and indexing before asking questions about it.";
        }

        String jobDescriptionBlock = (jobDescription == null || jobDescription.isBlank())
                ? ""
                : """

                --- TARGET JOB DESCRIPTION START ---
                %s
                --- TARGET JOB DESCRIPTION END ---
                """.formatted(jobDescription);

        String systemPrompt = SYSTEM_PROMPT_TEMPLATE.formatted(ragContext, jobDescriptionBlock);

        String effectiveUserMessage = (userMessage == null || userMessage.isBlank())
                ? "Generate some mock interview questions based on my resume."
                : userMessage;

        return callWithRetry(chatClient, systemPrompt, effectiveUserMessage);
    }

    private String retrieveResumeContext(UUID resumeId, String query) {
        FilterExpressionBuilder filterBuilder = new FilterExpressionBuilder();

        SearchRequest searchRequest = SearchRequest.builder()
                .query((query == null || query.isBlank()) ? "experience projects skills" : query)
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

