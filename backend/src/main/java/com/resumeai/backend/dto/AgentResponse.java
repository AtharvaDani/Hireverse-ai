package com.resumeai.backend.dto;

/**
 * What the React dashboard renders as the "AI Report" (step 8 in the architecture).
 *
 * @param agentUsed which agent the Supervisor routed to (e.g. "RESUME_AGENT") -
 *                   shown in the UI so the user can see the routing decision
 * @param response   the generated report / answer text
 */
public record AgentResponse(
        String agentUsed,
        String response
) {
}
