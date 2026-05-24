package com.att.tdp.issueflow.ticket;

import com.att.tdp.issueflow.auditlog.AuditAction;
import com.att.tdp.issueflow.auditlog.AuditEntityType;
import com.att.tdp.issueflow.auditlog.AuditLogService;
import com.att.tdp.issueflow.common.SecurityUtils;
import com.att.tdp.issueflow.common.exception.BadRequestException;
import com.att.tdp.issueflow.common.exception.ConflictException;
import com.att.tdp.issueflow.common.exception.ResourceNotFoundException;
import com.att.tdp.issueflow.ticket.dto.DependencyResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
public class TicketDependencyServiceImpl implements TicketDependencyService {

    private final TicketRepository ticketRepository;
    private final TicketDependencyRepository dependencyRepository;
    private final AuditLogService auditLogService;
    private final SecurityUtils securityUtils;

    @Override
    public void addDependency(Long ticketId, Long blockedById) {
        if (ticketId.equals(blockedById)) {
            throw new BadRequestException("A ticket cannot depend on itself");
        }

        Ticket ticket = ticketRepository.findByIdAndDeletedAtIsNull(ticketId)
                .orElseThrow(() -> new ResourceNotFoundException("Ticket not found with id: " + ticketId));

        Ticket blocker = ticketRepository.findByIdAndDeletedAtIsNull(blockedById)
                .orElseThrow(() -> new ResourceNotFoundException("Ticket not found with id: " + blockedById));

        if (!ticket.getProject().getId().equals(blocker.getProject().getId())) {
            throw new BadRequestException("Both tickets must belong to the same project");
        }

        TicketDependencyId depId = new TicketDependencyId(ticketId, blockedById);
        if (dependencyRepository.existsById(depId)) {
            throw new ConflictException("Dependency already exists");
        }

        TicketDependency dependency = TicketDependency.builder()
                .id(depId)
                .ticket(ticket)
                .blockedBy(blocker)
                .build();

        dependencyRepository.saveAndFlush(dependency);

        auditLogService.record(AuditAction.CREATE, AuditEntityType.DEPENDENCY, ticketId,
                securityUtils.getCurrentUserId(),
                "Ticket " + ticketId + " blocked by ticket " + blockedById);
    }

    @Override
    @Transactional(readOnly = true)
    public List<DependencyResponse> getDependencies(Long ticketId) {
        ticketRepository.findByIdAndDeletedAtIsNull(ticketId)
                .orElseThrow(() -> new ResourceNotFoundException("Ticket not found with id: " + ticketId));
        return dependencyRepository.findBlockersByTicketId(ticketId).stream()
                .map(DependencyResponse::from)
                .toList();
    }

    @Override
    public void removeDependency(Long ticketId, Long blockedById) {
        TicketDependencyId depId = new TicketDependencyId(ticketId, blockedById);
        if (!dependencyRepository.existsById(depId)) {
            throw new ResourceNotFoundException(
                    "Dependency not found: ticket " + ticketId + " blocked by " + blockedById);
        }

        dependencyRepository.deleteByTicketIdAndBlockedById(ticketId, blockedById);

        auditLogService.record(AuditAction.DELETE, AuditEntityType.DEPENDENCY, ticketId,
                securityUtils.getCurrentUserId(),
                "Dependency on ticket " + blockedById + " removed");
    }
}
