package com.resumeai.backend.controller;

import com.resumeai.backend.dto.AgentRequest;
import com.resumeai.backend.dto.AgentResponse;
import com.resumeai.backend.dto.DirectAgentRequest;
import com.resumeai.backend.dto.ResumeUploadResponse;
import com.resumeai.backend.model.Resume;
import com.resumeai.backend.service.ResumeService;
import com.resumeai.backend.service.agent.AgentType;
import com.resumeai.backend.service.agent.SupervisorAgentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

/**
 * Entry point for the React frontend. Covers:
 *   Step 2: "Send PDF + User Request"      -> POST /api/resumes/upload
 *   Step 6-8: routing + agent + response    -> POST /api/resumes/{id}/chat
 */
@RestController
@RequestMapping("/api/resumes")
@RequiredArgsConstructor
public class ResumeController {

    private final ResumeService resumeService;
    private final SupervisorAgentService supervisorAgentService;

    @PostMapping("/upload")
    public ResponseEntity<ResumeUploadResponse> uploadResume(@RequestParam("file") MultipartFile file) {
        Resume resume = resumeService.uploadAndProcess(file);

        return ResponseEntity.status(HttpStatus.CREATED).body(new ResumeUploadResponse(
                resume.getId().toString(),
                resume.getFileName(),
                resume.isIndexed(),
                "Resume uploaded and indexed successfully."
        ));
    }

    @PostMapping("/{resumeId}/chat")
    public ResponseEntity<AgentResponse> chat(
            @PathVariable String resumeId,
            @Valid @RequestBody AgentRequest request
    ) {
        UUID id = UUID.fromString(resumeId);
        // Ensure the resume exists before routing to an agent, so we fail
        // fast with a clear 404 instead of the agent silently finding no RAG context.
        resumeService.getResumeOrThrow(id);

        var result = supervisorAgentService.routeAndHandle(id, request.message(), request.jobDescription());

        return ResponseEntity.ok(new AgentResponse(
                result.agentUsed().name(),
                result.response()
        ));
    }

    /**
     * Calls a specific agent directly by name, bypassing the Supervisor's
     * routing decision. Used by frontend tabs that map 1:1 to a known
     * agent (Resume Score, ATS Gap Checker, Rewrite Optimizer) where the
     * user already picked the tool by clicking the tab.
     */
    @PostMapping("/{resumeId}/agent")
    public ResponseEntity<AgentResponse> callAgentDirectly(
            @PathVariable String resumeId,
            @Valid @RequestBody DirectAgentRequest request
    ) {
        UUID id = UUID.fromString(resumeId);
        resumeService.getResumeOrThrow(id);

        AgentType agentType = AgentType.valueOf(request.agentType());
        var result = supervisorAgentService.callDirect(agentType, id, request.message(), request.jobDescription());

        return ResponseEntity.ok(new AgentResponse(
                result.agentUsed().name(),
                result.response()
        ));
    }
}
