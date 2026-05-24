package com.att.tdp.issueflow.ticket;

import com.att.tdp.issueflow.auditlog.AuditAction;
import com.att.tdp.issueflow.auditlog.AuditEntityType;
import com.att.tdp.issueflow.auditlog.AuditLogService;
import com.att.tdp.issueflow.common.SecurityUtils;
import com.att.tdp.issueflow.common.exception.BadRequestException;
import com.att.tdp.issueflow.common.exception.BusinessRuleException;
import com.att.tdp.issueflow.common.exception.ResourceNotFoundException;
import com.att.tdp.issueflow.project.Project;
import com.att.tdp.issueflow.project.ProjectRepository;
import com.att.tdp.issueflow.ticket.dto.CreateTicketRequest;
import com.att.tdp.issueflow.ticket.dto.TicketResponse;
import com.att.tdp.issueflow.ticket.dto.UpdateTicketRequest;
import com.att.tdp.issueflow.user.User;
import com.att.tdp.issueflow.user.UserRepository;
import com.att.tdp.issueflow.user.UserWorkloadView;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
public class TicketServiceImpl implements TicketService {

    private final TicketRepository ticketRepository;
    private final TicketDependencyRepository ticketDependencyRepository;
    private final ProjectRepository projectRepository;
    private final UserRepository userRepository;
    private final AuditLogService auditLogService;
    private final SecurityUtils securityUtils;

    @Override
    public TicketResponse createTicket(CreateTicketRequest request) {
        Project project = projectRepository.findByIdAndDeletedAtIsNull(request.getProjectId())
                .orElseThrow(() -> new ResourceNotFoundException("Project not found with id: " + request.getProjectId()));

        User assignee = null;
        UserWorkloadView autoAssignmentTarget = null;
        if (request.getAssigneeId() != null) {
            assignee = userRepository.findById(request.getAssigneeId())
                    .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + request.getAssigneeId()));
        } else {
            List<UserWorkloadView> developers = userRepository.findDevelopersForAssignment(request.getProjectId());
            if (!developers.isEmpty()) {
                UserWorkloadView target = developers.get(0);
                autoAssignmentTarget = target;
                assignee = userRepository.findById(target.getUserId())
                        .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + target.getUserId()));
            }
        }

        Ticket ticket = Ticket.builder()
                .title(request.getTitle())
                .description(request.getDescription())
                .status(request.getStatus() != null ? request.getStatus() : TicketStatus.TODO)
                .priority(request.getPriority())
                .type(request.getType())
                .project(project)
                .assignee(assignee)
                .dueDate(request.getDueDate())
                .build();

        Ticket saved = ticketRepository.save(ticket);
        auditLogService.record(AuditAction.CREATE, AuditEntityType.TICKET, saved.getId(),
                securityUtils.getCurrentUserId(),
                "Ticket '" + saved.getTitle() + "' created with status " + saved.getStatus());
        if (autoAssignmentTarget != null) {
            auditLogService.record(AuditAction.AUTO_ASSIGN, AuditEntityType.TICKET, saved.getId(),
                    null,
                    "Auto-assigned to user " + autoAssignmentTarget.getUsername()
                            + " (workload: " + autoAssignmentTarget.getOpenTicketCount() + " open tickets)");
        }
        return TicketResponse.from(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public TicketResponse getTicketById(Long id) {
        return TicketResponse.from(findActiveOrThrow(id));
    }

    @Override
    @Transactional(readOnly = true)
    public List<TicketResponse> getTicketsByProject(Long projectId) {
        if (!projectRepository.existsById(projectId)) {
            throw new ResourceNotFoundException("Project not found with id: " + projectId);
        }
        return ticketRepository.findAllByProjectIdAndDeletedAtIsNull(projectId).stream()
                .map(TicketResponse::from)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<TicketResponse> getDeletedTickets(Long projectId) {
        if (!projectRepository.existsById(projectId)) {
            throw new ResourceNotFoundException("Project not found with id: " + projectId);
        }
        return ticketRepository.findAllByProjectIdAndDeletedAtIsNotNull(projectId).stream()
                .map(TicketResponse::from)
                .toList();
    }

    @Override
    public TicketResponse updateTicket(Long id, UpdateTicketRequest request) {
        Ticket ticket = findActiveOrThrow(id);

        if (ticket.getStatus() == TicketStatus.DONE) {
            throw new BadRequestException("Cannot update a ticket with status DONE");
        }

        TicketStatus oldStatus = ticket.getStatus();

        if (request.getStatus() != null) {
            validateStatusTransition(ticket.getStatus(), request.getStatus());
            if (request.getStatus() == TicketStatus.DONE) {
                List<Ticket> unresolvedBlockers = ticketDependencyRepository.findUnresolvedBlockers(id);
                if (!unresolvedBlockers.isEmpty()) {
                    throw new BusinessRuleException("Ticket has unresolved blockers");
                }
            }
            ticket.setStatus(request.getStatus());
        }
        if (request.getTitle() != null) {
            ticket.setTitle(request.getTitle());
        }
        if (request.getDescription() != null) {
            ticket.setDescription(request.getDescription());
        }
        if (request.getPriority() != null) {
            ticket.setPriority(request.getPriority());
            ticket.setOverdue(false);
        }
        if (request.getAssigneeId() != null) {
            User assignee = userRepository.findById(request.getAssigneeId())
                    .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + request.getAssigneeId()));
            ticket.setAssignee(assignee);
        }
        if (request.getDueDate() != null) {
            ticket.setDueDate(request.getDueDate());
        }

        Ticket saved = ticketRepository.save(ticket);
        String details = request.getStatus() != null && !request.getStatus().equals(oldStatus)
                ? "Status changed from " + oldStatus + " to " + saved.getStatus()
                : "Ticket '" + saved.getTitle() + "' updated";
        auditLogService.record(AuditAction.UPDATE, AuditEntityType.TICKET, saved.getId(),
                securityUtils.getCurrentUserId(), details);
        return TicketResponse.from(saved);
    }

    @Override
    public void deleteTicket(Long id) {
        Ticket ticket = findActiveOrThrow(id);
        ticket.setDeletedAt(LocalDateTime.now());
        ticketRepository.save(ticket);
        auditLogService.record(AuditAction.DELETE, AuditEntityType.TICKET, id,
                securityUtils.getCurrentUserId(),
                "Ticket '" + ticket.getTitle() + "' soft-deleted");
    }

    @Override
    public void restoreTicket(Long id) {
        Ticket ticket = ticketRepository.findByIdAndDeletedAtIsNotNull(id)
                .orElseThrow(() -> new ResourceNotFoundException("Ticket not found with id: " + id));

        if (ticket.getProject().getDeletedAt() != null) {
            throw new BadRequestException("Cannot restore ticket: parent project is deleted");
        }

        ticket.setDeletedAt(null);
        ticketRepository.save(ticket);
        auditLogService.record(AuditAction.RESTORE, AuditEntityType.TICKET, id,
                securityUtils.getCurrentUserId(),
                "Ticket '" + ticket.getTitle() + "' restored");
    }

    private Ticket findActiveOrThrow(Long id) {
        return ticketRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new ResourceNotFoundException("Ticket not found with id: " + id));
    }

    private void validateStatusTransition(TicketStatus current, TicketStatus next) {
        if (next.ordinal() != current.ordinal() + 1) {
            throw new BadRequestException(
                    "Invalid status transition: " + current + " → " + next
                            + ". Status must advance exactly one step at a time.");
        }
    }
}
