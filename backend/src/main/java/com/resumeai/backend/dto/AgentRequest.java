package com.resumeai.backend.dto;

import jakarta.validation.constraints.NotBlank;

import java.util.UUID;

/**
 * Sent by the frontend after a resume has been uploaded, to ask
 * the Supervisor agent a question about it (step 6 in the architecture:
 * "Decide which agent to call").
 */
public record AgentRequest(
        @NotBlank(message = "resumeId is required")
        String resumeId,

        @NotBlank(message = "message cannot be empty")
        String message,

        /**
         * Optional job description text from the dedicated UI field.
         * Used by JobAgent (and optionally InterviewAgent) to tailor
         * their analysis to a specific role. May be null or blank.
         */
        String jobDescription
) {
}
