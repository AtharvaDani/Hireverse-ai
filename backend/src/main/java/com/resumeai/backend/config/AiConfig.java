package com.resumeai.backend.config;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/**
 * Single ChatClient bean pointing to Groq via OpenAI-compatible API.
 * All agents including MockInterviewAgent use this.
 * MockInterviewAgent sends text-only (Groq doesn't support audio/video).
 */
@Configuration
public class AiConfig {

    @Bean
    @Primary
    public ChatClient groqChatClient(ChatClient.Builder builder) {
        return builder.build();
    }
}
