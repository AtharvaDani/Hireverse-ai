package com.resumeai.backend.repository;

import com.resumeai.backend.model.Resume;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface ResumeRepository extends JpaRepository<Resume, UUID> {
}
