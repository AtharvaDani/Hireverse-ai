package com.resumeai.backend.service;

import org.springframework.ai.document.Document;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Step 5 in the architecture: "Create Knowledge Base".
 *
 * Takes the raw extracted resume text, splits it into overlapping chunks
 * (so a single resume isn't crammed into one giant, hard-to-retrieve blob),
 * and writes each chunk into the pgvector store with metadata tagging
 * which resumeId it belongs to. That tag is what lets the agent layer
 * later do a similarity search scoped to ONE specific resume rather than
 * mixing chunks from every resume ever uploaded.
 */
@Service
public class ResumeIndexingService {

    /** Metadata key used to scope RAG retrieval to a single resume. */
    public static final String RESUME_ID_METADATA_KEY = "resumeId";

    private final VectorStore vectorStore;
    private final TokenTextSplitter textSplitter;

    public ResumeIndexingService(VectorStore vectorStore) {
        this.vectorStore = vectorStore;
        // Default chunk size (~800 tokens) with overlap is a reasonable
        // starting point for resume-length documents (1-3 pages).
        this.textSplitter = new TokenTextSplitter();
    }

    /**
     * Splits the resume text and writes the chunks into the vector store,
     * tagging every chunk with the owning resumeId.
     */
    public void indexResume(UUID resumeId, String extractedText) {
        Document fullDocument = new Document(
                extractedText,
                Map.of(RESUME_ID_METADATA_KEY, resumeId.toString())
        );

        List<Document> chunks = textSplitter.apply(List.of(fullDocument));

        // Re-stamp metadata on every chunk - some splitters can drop
        // custom metadata, so we defend against that explicitly here.
        List<Document> taggedChunks = chunks.stream()
                .map(chunk -> Document.builder()
                        .id(UUID.randomUUID().toString())
                        .text(chunk.getText())
                        .metadata(RESUME_ID_METADATA_KEY, resumeId.toString())
                        .build())
                .toList();

        vectorStore.add(taggedChunks);
    }
}
