package com.resumeai.backend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.context.annotation.ComponentScan;

@SpringBootApplication(
    exclude = {
        // Groq (OpenAI-compatible) is used for CHAT only.
        // Explicitly exclude its embedding auto-config so pgvector
        // has exactly one embedding bean (Gemini) with no ambiguity.
        org.springframework.ai.model.openai.autoconfigure.OpenAiEmbeddingAutoConfiguration.class
    }
)
public class BackendApplication {
    public static void main(String[] args) {
        SpringApplication.run(BackendApplication.class, args);
    }
}
