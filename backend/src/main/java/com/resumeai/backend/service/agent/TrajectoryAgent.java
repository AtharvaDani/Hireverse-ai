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
 * Career Trajectory Agent — predicts 3 realistic career paths with timelines.
 *
 * Returns strict JSON:
 * {
 *   "currentLevel": "Junior Software Engineer",
 *   "yearsExperience": 2,
 *   "paths": [
 *     {
 *       "title": "Senior Software Engineer",
 *       "timeline": "6-12 months",
 *       "probability": 85,
 *       "skillsNeeded": ["System Design", "Mentoring"],
 *       "actionPlan": ["...","...","..."]
 *     },
 *     ...
 *   ],
 *   "immediateActions": ["...","...","..."],
 *   "salaryRange": "$90k - $130k (India: ₹18L - ₹35L)"
 * }
 */
@Service
public class TrajectoryAgent extends BaseAgent {

    private static final String SYSTEM = """
            You are a career strategist with 20 years of experience in tech hiring.
            Analyze this resume and predict 3 realistic career paths.

            Return ONLY this JSON — no markdown, no explanation:
            {
              "currentLevel": "current role/level in one line",
              "yearsExperience": 0,
              "paths": [
                {
                  "title": "role title",
                  "timeline": "X-Y months",
                  "probability": 0,
                  "color": "#hex",
                  "skillsNeeded": ["skill1","skill2","skill3"],
                  "actionPlan": ["specific action 1","specific action 2","specific action 3"]
                }
              ],
              "immediateActions": ["do this week 1","do this week 2","do this week 3"],
              "salaryRange": "realistic range for their level and location"
            }

            Rules:
            - Path 1: most realistic next step (6-18 months), probability 70-90%,  color "#10b981"
            - Path 2: stretch goal (1-3 years),               probability 40-65%,  color "#7c3aed"
            - Path 3: pivot/alternative path,                  probability 30-50%,  color "#f59e0b"
            - skillsNeeded must be specific skills they are MISSING, not ones they have
            - actionPlan must be concrete (e.g. "Build a system design project on GitHub", not "improve skills")
            - immediateActions: 3 things they can do THIS WEEK
            """;

    private final ChatClient chatClient;
    private final VectorStore vectorStore;

    public TrajectoryAgent(ChatClient chatClient, VectorStore vectorStore) {
        this.chatClient = chatClient;
        this.vectorStore = vectorStore;
    }

    @Override public AgentType getType() { return AgentType.TRAJECTORY_AGENT; }

    @Override
    public String handle(UUID resumeId, String userMessage, String jobDescription) {
        String context = retrieve(resumeId);
        return callWithRetry(chatClient, SYSTEM,
                "Resume:\n" + context + "\n\nTarget role hint: " +
                (jobDescription != null && !jobDescription.isBlank() ? jobDescription : "not specified") +
                "\n\nGenerate the career trajectory JSON.");
    }

    private String retrieve(UUID resumeId) {
        FilterExpressionBuilder fb = new FilterExpressionBuilder();
        return vectorStore.similaritySearch(SearchRequest.builder()
                .query("work experience job titles skills education certifications")
                .topK(10).similarityThreshold(0.3)
                .filterExpression(fb.eq(ResumeIndexingService.RESUME_ID_METADATA_KEY,
                        resumeId.toString()).build()).build())
                .stream().map(Document::getText).collect(Collectors.joining("\n---\n"));
    }
}
