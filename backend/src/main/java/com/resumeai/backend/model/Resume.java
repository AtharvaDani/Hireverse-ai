package com.resumeai.backend.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Stores metadata + extracted raw text for an uploaded resume.
 * The actual vector embeddings of this text live separately in the
 * pgvector "resume_vector_store" table (managed by Spring AI's VectorStore),
 * linked back to this entity via resumeId in each chunk's metadata.
 */
@Entity
@Table(name = "resumes")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Resume {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(nullable = false)
    private String fileName;

    @Lob
    @Column(columnDefinition = "TEXT")
    private String extractedText;

    @Column(nullable = false)
    private LocalDateTime uploadedAt;

    /**
     * Tracks whether the extracted text has been chunked + embedded
     * into the vector store yet, so the agent layer knows when RAG
     * retrieval is ready to use for this resume.
     */
    @Column(nullable = false)
    private boolean indexed;

    @PrePersist
    public void prePersist() {
        if (uploadedAt == null) {
            uploadedAt = LocalDateTime.now();
        }
    }
}
