package com.att.tdp.issueflow.project;

import com.att.tdp.issueflow.auditlog.AuditAction;
import com.att.tdp.issueflow.auditlog.AuditEntityType;
import com.att.tdp.issueflow.auditlog.AuditLogService;
import com.att.tdp.issueflow.common.SecurityUtils;
import com.att.tdp.issueflow.common.exception.ResourceNotFoundException;
import com.att.tdp.issueflow.project.dto.CreateProjectRequest;
import com.att.tdp.issueflow.project.dto.ProjectResponse;
import com.att.tdp.issueflow.project.dto.UpdateProjectRequest;
import com.att.tdp.issueflow.project.dto.WorkloadResponse;
import com.att.tdp.issueflow.ticket.Ticket;
import com.att.tdp.issueflow.ticket.TicketRepository;
import com.att.tdp.issueflow.user.User;
import com.att.tdp.issueflow.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
public class ProjectServiceImpl implements ProjectService {

    private final ProjectRepository projectRepository;
    private final TicketRepository ticketRepository;
    private final UserRepository userRepository;
    private final AuditLogService auditLogService;
    private final SecurityUtils securityUtils;

    @Override
    @Transactional(readOnly = true)
    public List<ProjectResponse> getAllProjects() {
        return projectRepository.findAllByDeletedAtIsNull().stream()
                .map(ProjectResponse::from)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<ProjectResponse> getDeletedProjects() {
        return projectRepository.findAllByDeletedAtIsNotNull().stream()
                .map(ProjectResponse::from)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public ProjectResponse getProjectById(Long id) {
        return ProjectResponse.from(findActiveOrThrow(id));
    }

    @Override
    public ProjectResponse createProject(CreateProjectRequest request) {
        User owner = userRepository.findById(request.getOwnerId())
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + request.getOwnerId()));

        Project project = Project.builder()
                .name(request.getName())
                .description(request.getDescription())
                .owner(owner)
                .build();

        Project saved = projectRepository.save(project);
        auditLogService.record(AuditAction.CREATE, AuditEntityType.PROJECT, saved.getId(),
                securityUtils.getCurrentUserId(),
                "Project '" + saved.getName() + "' created");
        return ProjectResponse.from(saved);
    }

    @Override
    public ProjectResponse updateProject(Long id, UpdateProjectRequest request) {
        Project project = findActiveOrThrow(id);

        if (request.getName() != null) {
            project.setName(request.getName());
        }
        if (request.getDescription() != null) {
            project.setDescription(request.getDescription());
        }

        Project saved = projectRepository.save(project);
        auditLogService.record(AuditAction.UPDATE, AuditEntityType.PROJECT, saved.getId(),
                securityUtils.getCurrentUserId(),
                "Project '" + saved.getName() + "' updated");
        return ProjectResponse.from(saved);
    }

    @Override
    public void deleteProject(Long id) {
        Project project = findActiveOrThrow(id);
        LocalDateTime now = LocalDateTime.now();

        // Cascade to active tickets only — tickets already individually deleted keep their own timestamp
        List<Ticket> activeTickets = ticketRepository.findAllByProjectIdAndDeletedAtIsNull(id);
        activeTickets.forEach(t -> t.setDeletedAt(now));
        ticketRepository.saveAll(activeTickets);

        project.setDeletedAt(now);
        projectRepository.save(project);

        auditLogService.record(AuditAction.DELETE, AuditEntityType.PROJECT, id,
                securityUtils.getCurrentUserId(),
                "Project soft-deleted; " + activeTickets.size() + " tickets cascade-deleted");
    }

    @Override
    public void restoreProject(Long id) {
        Project project = projectRepository.findByIdAndDeletedAtIsNotNull(id)
                .orElseThrow(() -> new ResourceNotFoundException("Project not found with id: " + id));

        LocalDateTime projectDeletedAt = project.getDeletedAt();

        project.setDeletedAt(null);
        projectRepository.save(project);

        // Restore only tickets that were cascade-deleted with this project (same timestamp)
        List<Ticket> cascadeTickets = ticketRepository.findCascadeDeletedByProjectIdAndDeletedAt(id, projectDeletedAt);
        cascadeTickets.forEach(t -> t.setDeletedAt(null));
        ticketRepository.saveAll(cascadeTickets);

        auditLogService.record(AuditAction.RESTORE, AuditEntityType.PROJECT, id,
                securityUtils.getCurrentUserId(),
                "Project restored; " + cascadeTickets.size() + " tickets restored");
    }

    @Override
    @Transactional(readOnly = true)
    public List<WorkloadResponse> getWorkload(Long projectId) {
        findActiveOrThrow(projectId);
        return userRepository.findDeveloperWorkloadByProject(projectId).stream()
                .map(WorkloadResponse::from)
                .toList();
    }

    private Project findActiveOrThrow(Long id) {
        return projectRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new ResourceNotFoundException("Project not found with id: " + id));
    }
}
