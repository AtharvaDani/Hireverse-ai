package com.resumeai.backend.service.agent;

import org.springframework.ai.chat.client.ChatClient;

/**
 * Base class for all agents.
 *
 * Provides a single callWithRetry() method that wraps every Gemini call
 * with automatic 429 back-off — so no agent ever crashes on rate limits.
 *
 * Retry policy:
 *   - Waits 16 s on first 429, doubles each time, caps at 60 s
 *   - Retries up to 3 times, then throws a user-friendly error
 *
 * Usage: replace  chatClient.prompt().system(s).user(u).call().content()
 *           with  callWithRetry(chatClient, s, u)
 */
public abstract class BaseAgent implements Agent {

    private static final int  MAX_ATTEMPTS    = 3;
    private static final long INITIAL_WAIT_MS = 16_000;

    protected String callWithRetry(ChatClient chatClient, String systemPrompt, String userMessage) {
        Exception last = null;
        long wait = INITIAL_WAIT_MS;

        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            try {
                return chatClient.prompt()
                        .system(systemPrompt)
                        .user(userMessage)
                        .call()
                        .content();
            } catch (Exception e) {
                if (!isRateLimit(e)) throw e;
                last = e;
                System.out.printf(
                    "[BaseAgent] Gemini 429 (attempt %d/%d) — waiting %d s…%n",
                    attempt, MAX_ATTEMPTS, wait / 1000);
                try { Thread.sleep(wait); }
                catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException("Interrupted during rate-limit back-off", ie);
                }
                wait = Math.min(wait * 2, 60_000);
            }
        }
        throw new RuntimeException(
            "Gemini rate limit persists after " + MAX_ATTEMPTS + " retries. " +
            "Please wait ~1 minute and try again. " +
            "(Free tier: 10 req/min on gemini-2.5-flash)", last);
    }

    private boolean isRateLimit(Exception e) {
        String msg = fullMsg(e);
        return msg.contains("429")
            || msg.toLowerCase().contains("quota")
            || msg.toLowerCase().contains("rate_limit")
            || msg.toLowerCase().contains("resource_exhausted")
            || msg.toLowerCase().contains("exhausted");
    }

    private String fullMsg(Throwable t) {
        StringBuilder sb = new StringBuilder();
        while (t != null) {
            if (t.getMessage() != null) sb.append(t.getMessage()).append(' ');
            t = t.getCause();
        }
        return sb.toString();
    }
}
