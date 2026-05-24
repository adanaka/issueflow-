package com.att.tdp.issueflow.project;

import com.att.tdp.issueflow.project.dto.CreateProjectRequest;
import com.att.tdp.issueflow.project.dto.ProjectResponse;
import com.att.tdp.issueflow.project.dto.UpdateProjectRequest;
import com.att.tdp.issueflow.project.dto.WorkloadResponse;
import com.att.tdp.issueflow.ticket.TicketService;
import com.att.tdp.issueflow.ticket.dto.TicketResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/projects")
@RequiredArgsConstructor
public class ProjectController {

    private final ProjectService projectService;
    private final TicketService ticketService;

    @GetMapping
    public ResponseEntity<List<ProjectResponse>> getAllProjects() {
        return ResponseEntity.ok(projectService.getAllProjects());
    }

    @GetMapping("/deleted")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<ProjectResponse>> getDeletedProjects() {
        return ResponseEntity.ok(projectService.getDeletedProjects());
    }

    @GetMapping("/{projectId}")
    public ResponseEntity<ProjectResponse> getProjectById(@PathVariable Long projectId) {
        return ResponseEntity.ok(projectService.getProjectById(projectId));
    }

    @PostMapping
    public ResponseEntity<ProjectResponse> createProject(@Valid @RequestBody CreateProjectRequest request) {
        return ResponseEntity.ok(projectService.createProject(request));
    }

    @PatchMapping("/{projectId}")
    public ResponseEntity<ProjectResponse> updateProject(
            @PathVariable Long projectId,
            @Valid @RequestBody UpdateProjectRequest request) {
        return ResponseEntity.ok(projectService.updateProject(projectId, request));
    }

    @DeleteMapping("/{projectId}")
    public ResponseEntity<Void> deleteProject(@PathVariable Long projectId) {
        projectService.deleteProject(projectId);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/{projectId}/restore")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> restoreProject(@PathVariable Long projectId) {
        projectService.restoreProject(projectId);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/{projectId}/tickets")
    public ResponseEntity<List<TicketResponse>> getTicketsByProject(@PathVariable Long projectId) {
        return ResponseEntity.ok(ticketService.getTicketsByProject(projectId));
    }

    @GetMapping("/{projectId}/workload")
    public ResponseEntity<List<WorkloadResponse>> getWorkload(@PathVariable Long projectId) {
        return ResponseEntity.ok(projectService.getWorkload(projectId));
    }
}
