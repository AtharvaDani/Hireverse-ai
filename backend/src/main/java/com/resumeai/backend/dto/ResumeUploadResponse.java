package com.resumeai.backend.dto;

public record ResumeUploadResponse(
        String resumeId,
        String fileName,
        boolean indexed,
        String message
) {
}
