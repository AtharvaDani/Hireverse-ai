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
 * DNA Agent — scores the resume across 6 dimensions and returns
 * structured JSON for the radar chart.
 *
 * Output format (strict JSON, no markdown):
 * {
 *   "dimensions": [
 *     { "name": "Technical Depth",  "score": 82, "reason": "..." },
 *     { "name": "Leadership",       "score": 45, "reason": "..." },
 *     { "name": "Communication",    "score": 70, "reason": "..." },
 *     { "name": "Impact & Results", "score": 88, "reason": "..." },
 *     { "name": "Clarity",          "score": 75, "reason": "..." },
 *     { "name": "ATS Readiness",    "score": 60, "reason": "..." }
 *   ],
 *   "summary": "One paragraph overall profile summary.",
 *   "topStrength": "Technical Depth",
 *   "biggestGap":  "Leadership"
 * }
 */
@Service
public class DnaAgent extends BaseAgent {

    private static final String SYSTEM = """
            You are a professional resume analyst. Analyze the resume excerpts
            and score this candidate across exactly these 6 dimensions (0-100):

            1. Technical Depth     — breadth and depth of technical skills, tools, languages
            2. Leadership          — team lead, mentoring, ownership, decision-making evidence
            3. Communication       — clarity of writing, action verbs, storytelling quality
            4. Impact & Results    — quantified achievements, business outcomes, metrics
            5. Clarity             — formatting, structure, conciseness, ATS-friendly layout
            6. ATS Readiness       — keyword density, standard section headers, no tables/graphics

            Return ONLY this JSON — no markdown, no explanation:
            {
              "dimensions": [
                { "name": "Technical Depth",  "score": 0, "reason": "one sentence" },
                { "name": "Leadership",       "score": 0, "reason": "one sentence" },
                { "name": "Communication",    "score": 0, "reason": "one sentence" },
                { "name": "Impact & Results", "score": 0, "reason": "one sentence" },
                { "name": "Clarity",          "score": 0, "reason": "one sentence" },
                { "name": "ATS Readiness",    "score": 0, "reason": "one sentence" }
              ],
              "summary": "2-3 sentence overall profile.",
              "topStrength": "dimension name",
              "biggestGap":  "dimension name"
            }
            """;

    private final ChatClient chatClient;
    private final VectorStore vectorStore;

    public DnaAgent(ChatClient chatClient, VectorStore vectorStore) {
        this.chatClient = chatClient;
        this.vectorStore = vectorStore;
    }

    @Override public AgentType getType() { return AgentType.DNA_AGENT; }

    @Override
    public String handle(UUID resumeId, String userMessage, String jobDescription) {
        String context = retrieve(resumeId);
        return callWithRetry(chatClient, SYSTEM,
                "Resume excerpts:\n" + context + "\n\nGenerate the DNA profile JSON.");
    }

    private String retrieve(UUID resumeId) {
        FilterExpressionBuilder fb = new FilterExpressionBuilder();
        return vectorStore.similaritySearch(SearchRequest.builder()
                .query("skills experience achievements leadership projects education")
                .topK(10).similarityThreshold(0.3)
                .filterExpression(fb.eq(ResumeIndexingService.RESUME_ID_METADATA_KEY,
                        resumeId.toString()).build()).build())
                .stream().map(Document::getText).collect(Collectors.joining("\n---\n"));
    }
}
