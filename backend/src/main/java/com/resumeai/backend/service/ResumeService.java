package com.resumeai.backend.service;

import com.resumeai.backend.model.Resume;
import com.resumeai.backend.repository.ResumeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.NoSuchElementException;
import java.util.UUID;

/**
 * Orchestrates steps 3, 4, and 5 of the architecture:
 *   3. Store Resume
 *   4. Extract Text from PDF      (delegated to PdfTextExtractionService)
 *   5. Create Knowledge Base      (delegated to ResumeIndexingService)
 */
@Service
@RequiredArgsConstructor
public class ResumeService {

    private final ResumeRepository resumeRepository;
    private final PdfTextExtractionService pdfTextExtractionService;
    private final ResumeIndexingService resumeIndexingService;

    public Resume uploadAndProcess(MultipartFile file) {
        String text = pdfTextExtractionService.extractText(file);

        Resume resume = Resume.builder()
                .fileName(file.getOriginalFilename())
                .extractedText(text)
                .indexed(false)
                .build();
        resume = resumeRepository.save(resume);

        try {
            resumeIndexingService.indexResume(resume.getId(), text);
            resume.setIndexed(true);
            resume = resumeRepository.save(resume);
        } catch (Exception e) {
            // The resume row still exists with indexed=false; the agent
            // layer checks this flag and can warn the user / allow retry
            // rather than silently answering with no RAG context.
            throw new RuntimeException(
                    "Resume was stored but indexing into the knowledge base failed: " + e.getMessage(), e);
        }

        return resume;
    }

    public Resume getResumeOrThrow(UUID resumeId) {
        return resumeRepository.findById(resumeId)
                .orElseThrow(() -> new NoSuchElementException("No resume found with id " + resumeId));
    }
}
