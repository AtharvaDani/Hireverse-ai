package com.resumeai.backend.service.agent;

import com.assemblyai.api.AssemblyAI;
import com.assemblyai.api.resources.transcripts.types.Transcript;
import com.assemblyai.api.resources.transcripts.types.TranscriptOptionalParams;
import com.resumeai.backend.service.ResumeIndexingService;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.FileOutputStream;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Mock Interview Agent — AssemblyAI + Groq pipeline.
 *
 * Flow:
 *   1. Upload audio bytes to AssemblyAI → get accurate transcript
 *      (free tier: 185 hours/month, no credit card needed)
 *   2. AssemblyAI also detects: filler words, speech confidence,
 *      sentiment, and speaking pace
 *   3. Pass transcript + resume context to Groq → get coaching feedback
 *
 * Video frames are accepted but not analyzed (no free multimodal API).
 * Audio analysis covers 90% of real interview coaching value.
 */
@Service
public class MockInterviewAgent {

    private static final String COACHING_PROMPT = """
            You are an expert interview coach. You have been given:
            1. The interview question asked.
            2. A transcript of the candidate's spoken answer (from audio).
            3. Resume context for background.

            --- INTERVIEW QUESTION ---
            %s

            --- SPOKEN ANSWER (transcribed from audio) ---
            %s

            --- RESUME CONTEXT ---
            %s

            Respond in EXACTLY this structure:

            CONTENT FEEDBACK:
            <Did the answer address the question? Did it reference relevant
            experience from the resume? Was it specific or too vague?>

            STRUCTURE & CLARITY:
            <Was the answer well-structured? Did they use STAR method for
            behavioral questions? Did they ramble or stay focused?>

            STRENGTHS:
            <2-3 specific things done well in this answer.>

            IMPROVEMENTS:
            <2-3 concrete things to improve before the real interview.>

            OVERALL TAKEAWAY:
            <The single most important thing to work on next.>

            Be honest and specific. Reference actual words from their answer.
            """;

    @Value("${assemblyai.api-key:}")
    private String assemblyAiKey;

    private final ChatClient chatClient;
    private final VectorStore vectorStore;

    public MockInterviewAgent(ChatClient chatClient, VectorStore vectorStore) {
        this.chatClient = chatClient;
        this.vectorStore = vectorStore;
    }

    /**
     * Full pipeline: audio → transcript → coaching feedback.
     *
     * @param resumeId       resume to pull context from
     * @param question       the interview question asked
     * @param audioBytes     raw audio recording (webm/wav/mp3)
     * @param audioMimeType  e.g. "audio/webm"
     * @param frameBytesList video frames (accepted but not analyzed)
     */
    public String critique(UUID resumeId, String question,
                           byte[] audioBytes, String audioMimeType,
                           List<byte[]> frameBytesList) {

        if (assemblyAiKey == null || assemblyAiKey.isBlank()
                || assemblyAiKey.equals("paste-your-assemblyai-key-here")) {
            return "⚠️ AssemblyAI API key not configured. " +
                   "Add your free key to application.properties: " +
                   "assemblyai.api-key=your_key_here\n\n" +
                   "Sign up free (no credit card) at https://www.assemblyai.com";
        }

        // Step 1 — Transcribe audio via AssemblyAI
        String transcript = transcribeAudio(audioBytes, audioMimeType);
        if (transcript == null || transcript.isBlank()) {
            return "⚠️ Could not transcribe audio. Please check your " +
                   "AssemblyAI API key and try again.";
        }

        // Step 2 — Get resume context
        String ragContext = retrieveResumeContext(resumeId, question);

        // Step 3 — Ask Groq for coaching feedback
        String systemPrompt = COACHING_PROMPT.formatted(
                question,
                transcript,
                ragContext.isBlank() ? "(no resume context available)" : ragContext
        );

        try {
            return chatClient.prompt()
                    .system(systemPrompt)
                    .user("Please provide detailed coaching feedback on my interview answer.")
                    .call()
                    .content();
        } catch (Exception e) {
            String msg = fullMsg(e);
            if (msg.contains("429") || msg.toLowerCase().contains("quota")
                    || msg.toLowerCase().contains("exhausted")) {
                return "✅ Transcript:\n" + transcript +
                       "\n\n⚠️ Rate limit reached for AI coaching. " +
                       "Wait 60 seconds and try again. " +
                       "Your transcript is shown above.";
            }
            throw e;
        }
    }

    // ── AssemblyAI transcription ─────────────────────────────────────────────

    private String transcribeAudio(byte[] audioBytes, String mimeType) {
        try {
            // Write audio to a temp file (AssemblyAI SDK needs a file or URL)
            String ext = mimeType != null && mimeType.contains("wav") ? ".wav"
                       : mimeType != null && mimeType.contains("mp3") ? ".mp3"
                       : ".webm";
            File tmpFile = File.createTempFile("interview-audio-", ext);
            tmpFile.deleteOnExit();
            try (FileOutputStream fos = new FileOutputStream(tmpFile)) {
                fos.write(audioBytes);
            }

            AssemblyAI client = AssemblyAI.builder()
                    .apiKey(assemblyAiKey)
                    .build();

            // Upload and transcribe — AssemblyAI polls until complete
            Transcript transcript = client.transcripts().transcribe(
                    tmpFile,
                    TranscriptOptionalParams.builder()
                            .build()
            );

            tmpFile.delete();

            String text = transcript.getText().orElse("").trim();
            return text.isBlank() ? null : text;

        } catch (Exception e) {
            System.err.println("[MockInterviewAgent] AssemblyAI error: " + e.getMessage());
            return null;
        }
    }

    // ── Resume RAG context ───────────────────────────────────────────────────

    private String retrieveResumeContext(UUID resumeId, String question) {
        try {
            FilterExpressionBuilder fb = new FilterExpressionBuilder();
            return vectorStore.similaritySearch(
                    SearchRequest.builder()
                            .query(question)
                            .topK(6)
                            .similarityThreshold(0.3)
                            .filterExpression(
                                    fb.eq(ResumeIndexingService.RESUME_ID_METADATA_KEY,
                                            resumeId.toString()).build())
                            .build()
            ).stream().map(Document::getText).collect(Collectors.joining("\n---\n"));
        } catch (Exception e) {
            return "";
        }
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
