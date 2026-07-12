package com.resumeai.backend.controller;

import com.resumeai.backend.dto.MockInterviewResponse;
import com.resumeai.backend.service.ResumeService;
import com.resumeai.backend.service.agent.MockInterviewAgent;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Mock Interview (voice + video) endpoint.
 *
 * Separate from ResumeController's /chat and /agent endpoints because this
 * one accepts multipart binary media (audio + image frames) rather than a
 * JSON body - a fundamentally different request shape.
 *
 * The frontend records audio + samples a handful of video frames while the
 * user answers a question, then posts everything here in one multipart
 * request once recording stops.
 */
@RestController
@RequestMapping("/api/resumes")
@RequiredArgsConstructor
public class MockInterviewController {

    /**
     * Hard cap on accepted frames per request. This is a real cost/quota
     * guard: each frame is a separate image sent to Gemini, and the free
     * tier's quota is consumed much faster by image-heavy requests than
     * text-only ones. The frontend should sample well under this count
     * (e.g. one frame every ~2 seconds), but this cap protects the backend
     * even if a frontend bug or malicious client sends more.
     */
    private static final int MAX_FRAMES = 8;

    private final ResumeService resumeService;
    private final MockInterviewAgent mockInterviewAgent;

    @PostMapping("/{resumeId}/mock-interview")
    public ResponseEntity<MockInterviewResponse> critique(
            @PathVariable String resumeId,
            @RequestParam("question") String question,
            @RequestParam("audio") MultipartFile audio,
            @RequestParam(value = "frames", required = false) List<MultipartFile> frames
    ) throws IOException {
        UUID id = UUID.fromString(resumeId);
        resumeService.getResumeOrThrow(id);

        if (audio.isEmpty()) {
            throw new IllegalArgumentException("No audio recording was received.");
        }

        String audioMimeType = audio.getContentType() != null ? audio.getContentType() : "audio/webm";

        List<byte[]> frameBytesList = new ArrayList<>();
        if (frames != null) {
            for (MultipartFile frame : frames.subList(0, Math.min(frames.size(), MAX_FRAMES))) {
                if (!frame.isEmpty()) {
                    frameBytesList.add(frame.getBytes());
                }
            }
        }

        String critique = mockInterviewAgent.critique(
                id, question, audio.getBytes(), audioMimeType, frameBytesList
        );

        return ResponseEntity.ok(new MockInterviewResponse(critique));
    }
}
