package com.resumeai.backend.service.agent;

import com.resumeai.backend.service.ResumeIndexingService;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.stereotype.Service;

import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Cover Letter Agent — generates 3 tone variants from resume + job description.
 *
 * Returns strict JSON:
 * {
 *   "professional": { "subject": "...", "body": "..." },
 *   "bold":         { "subject": "...", "body": "..." },
 *   "storytelling": { "subject": "...", "body": "..." },
 *   "keyPointsUsed": ["resume point 1", "resume point 2", "resume point 3"]
 * }
 */
@Service
public class CoverLetterAgent extends BaseAgent {

    private static final String SYSTEM = """
            You are an expert cover letter writer who has helped 10,000+ candidates
            land jobs at top companies.

            Given the resume context and job description, write 3 cover letter variants.
            Each must be tailored — referencing specific skills and achievements from
            the resume that match the job requirements.

            Return ONLY this JSON — no markdown, no explanation, no preamble:
            {
              "professional": {
                "subject": "Application for [Role] — [Candidate Name]",
                "body": "full cover letter text, 3-4 paragraphs, formal professional tone"
              },
              "bold": {
                "subject": "subject line",
                "body": "full cover letter, opens with a striking statement, confident assertive tone"
              },
              "storytelling": {
                "subject": "subject line",
                "body": "full cover letter, opens with a brief personal story or moment, warm narrative tone"
              },
              "keyPointsUsed": [
                "specific resume achievement used in the letters",
                "specific skill matched to job",
                "specific experience referenced"
              ]
            }

            Rules:
            - Each body must be 200-300 words
            - Must reference at least 2 specific achievements/skills from the resume
            - Must address the job description's key requirements
            - End each with a clear call to action
            - Use the candidate's actual name if found in resume
            """;

    private final ChatClient chatClient;
    private final VectorStore vectorStore;

    public CoverLetterAgent(ChatClient chatClient, VectorStore vectorStore) {
        this.chatClient = chatClient;
        this.vectorStore = vectorStore;
    }

    @Override public AgentType getType() { return AgentType.COVER_LETTER_AGENT; }

    @Override
    public String handle(UUID resumeId, String userMessage, String jobDescription) {
        String context = retrieve(resumeId);
        String jd = (jobDescription != null && !jobDescription.isBlank())
                ? jobDescription : "General application — infer a suitable role from the resume.";
        return callWithRetry(chatClient, SYSTEM,
                "Resume:\n" + context + "\n\nJob Description:\n" + jd +
                "\n\nGenerate 3 cover letter variants as JSON.");
    }

    private String retrieve(UUID resumeId) {
        FilterExpressionBuilder fb = new FilterExpressionBuilder();
        return vectorStore.similaritySearch(SearchRequest.builder()
                .query("work experience achievements skills projects education name contact")
                .topK(10).similarityThreshold(0.3)
                .filterExpression(fb.eq(ResumeIndexingService.RESUME_ID_METADATA_KEY,
                        resumeId.toString()).build()).build())
                .stream().map(Document::getText).collect(Collectors.joining("\n---\n"));
    }
}
