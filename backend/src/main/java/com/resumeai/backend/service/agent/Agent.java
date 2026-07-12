package com.resumeai.backend.service.agent;

import java.util.UUID;

/**
 * Contract every specialized agent (Resume, Job, Interview, ...) implements.
 *
 * This is what makes the Supervisor's job mechanical: it doesn't need to
 * know HOW each agent works internally (its prompt, its RAG strategy),
 * only that every agent can answer (resumeId, userMessage) -> String.
 *
 * To add a new agent later: implement this interface, give it a unique
 * AgentType, register it as a @Service bean, and the Supervisor will
 * pick it up automatically (see SupervisorAgentService).
 */
public interface Agent {

    /**
     * Unique identifier for this agent, used by the Supervisor's routing
     * decision and echoed back to the frontend in AgentResponse.agentUsed().
     */
    AgentType getType();

    /**
     * Runs this agent against a specific resume's knowledge base.
     *
     * @param resumeId       which resume's RAG context to retrieve from
     * @param userMessage    the user's free-text request/question
     * @param jobDescription optional job description text, used by agents
     *                       that compare the resume against a specific role
     *                       (e.g. JobAgent). Agents that don't need it can
     *                       simply ignore this parameter. May be null or blank.
     * @return the generated report/answer text
     */
    String handle(UUID resumeId, String userMessage, String jobDescription);
}
