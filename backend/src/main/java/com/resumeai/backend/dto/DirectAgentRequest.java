package com.resumeai.backend.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Sent by frontend tabs (Resume Score, ATS Gap Checker, Rewrite Optimizer)
 * that map 1:1 to a specific agent and don't need the Supervisor's LLM
 * routing decision - the user already picked the tool by clicking the tab.
 */
public record DirectAgentRequest(
        @NotBlank(message = "agentType is required")
        String agentType,

        /**
         * Free-text instruction. Optional - each agent has a sensible
         * default if this is blank (e.g. ScoreAgent defaults to "Score my
         * resume." if no custom message is given).
         */
        String message,

        /**
         * Required for ATS_GAP_AGENT, optional for REWRITE_AGENT (tailors
         * rewrites toward a role), ignored by SCORE_AGENT.
         */
        String jobDescription
) {
}
