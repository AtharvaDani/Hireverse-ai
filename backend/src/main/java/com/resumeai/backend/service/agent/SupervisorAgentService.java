package com.resumeai.backend.service.agent;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Step 6 in the architecture: "Decide which agent to call".
 *
 * This is the routing brain. It is intentionally generic: it has NO
 * knowledge of how ResumeAgent/JobAgent/InterviewAgent actually work
 * internally - it just knows their AgentType and a one-line description,
 * and asks Gemini to classify the user's intent into one of them.
 *
 * To add Job Agent / Interview Agent for real later:
 *   1. Implement the Agent interface (copy ResumeAgent.java as a template)
 *   2. Register it as a @Service bean
 *   3. Spring will auto-inject it into the `agents` list below - nothing
 *      else in this class needs to change.
 */
@Service
public class SupervisorAgentService {

    private static final String ROUTING_PROMPT_TEMPLATE = """
            You are a routing supervisor for a multi-agent career assistant.
            Decide which ONE agent should handle the user's request below.

            Available agents:
            %s

            Respond with ONLY the agent's exact identifier (e.g. RESUME_AGENT)
            and nothing else - no punctuation, no explanation.

            User request: "%s"
            """;

    private final ChatClient chatClient;
    private final Map<AgentType, Agent> agentsByType;

    public SupervisorAgentService(ChatClient.Builder chatClientBuilder, List<Agent> agents) {
        this.chatClient = chatClientBuilder.build();
        this.agentsByType = agents.stream()
                .collect(Collectors.toMap(Agent::getType, a -> a));
    }

    /**
     * Routes the request to the appropriate agent and returns both the
     * agent's answer and which agent was chosen (so the frontend dashboard
     * can show the routing decision, per the architecture's step 6->8 flow).
     *
     * @param jobDescription optional - if the user filled in the job
     *                       description field in the UI, it's passed here.
     *                       Its presence also nudges routing toward
     *                       JOB_AGENT even for a generic message like
     *                       "what do you think?".
     */
    public RoutingResult routeAndHandle(UUID resumeId, String userMessage, String jobDescription) {
        AgentType chosen = decideAgent(userMessage, jobDescription);
        Agent agent = agentsByType.get(chosen);

        if (agent == null) {
            // Falls back to Resume Agent if the LLM picked an agent that
            // isn't registered as a bean for some reason.
            agent = agentsByType.get(AgentType.RESUME_AGENT);
            chosen = AgentType.RESUME_AGENT;
        }

        String response = agent.handle(resumeId, userMessage, jobDescription);
        return new RoutingResult(chosen, response);
    }

    private AgentType decideAgent(String userMessage, String jobDescription) {
        String agentDescriptions = """
                - RESUME_AGENT: reviews, critiques, or analyzes the content
                  of the user's uploaded resume (strengths, weaknesses,
                  formatting, wording, ATS-friendliness, general feedback)
                - JOB_AGENT: matches the resume against a specific job
                  description or role, or suggests jobs/roles to apply for
                - INTERVIEW_AGENT: generates interview questions, mock
                  interview practice, or answer coaching based on the resume
                """;

        String jobDescriptionHint = (jobDescription != null && !jobDescription.isBlank())
                ? "\nNote: the user has also filled in a job description field in the UI, "
                + "which is a strong signal they want JOB_AGENT - prefer JOB_AGENT unless "
                + "their request clearly and explicitly asks for something else "
                + "(e.g. explicitly asking for interview questions or general resume feedback)."
                : "";

        String prompt = ROUTING_PROMPT_TEMPLATE.formatted(agentDescriptions, userMessage) + jobDescriptionHint;

        String rawDecision = callWithRetry(prompt)
                .trim()
                .toUpperCase();

        try {
            return AgentType.valueOf(rawDecision);
        } catch (IllegalArgumentException e) {
            // LLM didn't return a clean enum value - default to the one
            // fully-implemented agent in this slice rather than failing.
            return AgentType.RESUME_AGENT;
        }
    }

    /**
     * Calls a specific agent directly, bypassing the LLM routing decision.
     * Used by frontend tabs that map 1:1 to a known agent (e.g. the Resume
     * Score, ATS Gap Checker, and Rewrite tabs) where the user has already
     * picked the tool by clicking the tab - there's nothing to "decide".
     *
     * @throws IllegalArgumentException if no agent is registered for the
     *                                  given type (shouldn't happen in
     *                                  practice since all 6 agent types
     *                                  have a @Service implementation).
     */
    public RoutingResult callDirect(AgentType agentType, UUID resumeId, String userMessage, String jobDescription) {
        Agent agent = agentsByType.get(agentType);
        if (agent == null) {
            throw new IllegalArgumentException("No agent registered for type " + agentType);
        }
        String response = agent.handle(resumeId, userMessage, jobDescription);
        return new RoutingResult(agentType, response);
    }

    /**
     * Calls the LLM with the given user prompt, automatically retrying on
     * transient failures (e.g. the "model currently experiencing high
     * demand" 503 errors seen on free-tier API access) with exponential
     * backoff, instead of surfacing the failure straight to the user.
     *
     * Only retries on errors that look transient (server-side 5xx errors,
     * connection timeouts/resets). Does NOT retry on errors that won't
     * resolve themselves (e.g. bad request, auth failure) - those fail
     * immediately since retrying them is pointless.
     */
    private String callWithRetry(String userPrompt) {
        int maxAttempts = 3;
        long backoffMillis = 1000; // start at 1s, doubles each retry

        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                return chatClient.prompt()
                        .user(userPrompt)
                        .call()
                        .content();
            } catch (HttpServerErrorException | ResourceAccessException e) {
                boolean isLastAttempt = attempt == maxAttempts;
                if (isLastAttempt) {
                    throw e;
                }
                try {
                    Thread.sleep(backoffMillis);
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    throw e;
                }
                backoffMillis *= 2;
            }
        }

        // Unreachable in practice (loop either returns or throws), but
        // keeps the compiler happy about all code paths returning a value.
        throw new IllegalStateException("callWithRetry exhausted attempts without returning or throwing");
    }

    public record RoutingResult(AgentType agentUsed, String response) {
    }
}
