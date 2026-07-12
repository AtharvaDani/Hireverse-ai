package com.resumeai.backend.config;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/**
 * Resolves an ambiguity that appears as soon as this project has TWO
 * ChatModel beans in context: Google GenAI (Gemini, used only for
 * embeddings now) and OpenAI-compatible (Groq, used for all text
 * generation - see application.properties spring.ai.openai.base-url).
 *
 * Without this bean, Spring AI's auto-configured ChatClient.Builder fails
 * to start with:
 *   "Parameter 1 of method chatClientBuilder ... required a single bean,
 *    but 2 were found: googleGenAiChatModel, openAiChatModel"
 *
 * Every agent class in service/agent/ injects a plain ChatClient.Builder
 * with no @Qualifier (this was fine when only Gemini existed). Marking
 * this bean @Primary means all of those existing injections now resolve
 * to Groq automatically - NO changes needed in any agent file.
 *
 * Gemini's ChatModel bean still exists in context (harmless, unused for
 * chat) because the embedding model auto-configuration depends on the
 * same Google GenAI starter; only the *embedding* bean from that starter
 * is actually used (see ResumeIndexingService / pgvector config).
 */
@Configuration
public class ChatClientConfig {

    @Bean
    @Primary
    public ChatClient.Builder chatClientBuilder(OpenAiChatModel openAiChatModel) {
        return ChatClient.builder(openAiChatModel);
    }
}
