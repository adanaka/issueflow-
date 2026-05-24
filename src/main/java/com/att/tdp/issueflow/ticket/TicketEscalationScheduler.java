package com.att.tdp.issueflow.ticket;

import com.att.tdp.issueflow.auditlog.AuditAction;
import com.att.tdp.issueflow.auditlog.AuditEntityType;
import com.att.tdp.issueflow.auditlog.AuditLogService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class TicketEscalationScheduler {

    private final TicketRepository ticketRepository;
    private final AuditLogService auditLogService;

    // Self-injection via proxy so that @Transactional(REQUIRES_NEW) on
    // escalateSingleTicket is applied by Spring AOP (direct this-calls bypass it).
    @Lazy
    @Autowired
    private TicketEscalationScheduler self;

    @Scheduled(cron = "${ticket.escalation.cron}")
    public void escalateOverdueTickets() {
        List<Ticket> eligible = ticketRepository.findEligibleForEscalation(
                LocalDateTime.now(), TicketStatus.DONE);
        for (Ticket ticket : eligible) {
            try {
                self.escalateSingleTicket(ticket);
            } catch (Exception e) {
                log.error("Failed to escalate ticket {}: {}", ticket.getId(), e.getMessage());
            }
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void escalateSingleTicket(Ticket ticket) {
        TicketPriority current = ticket.getPriority();
        String details = switch (current) {
            case LOW -> {
                ticket.setPriority(TicketPriority.MEDIUM);
                yield "Priority escalated from LOW to MEDIUM (overdue)";
            }
            case MEDIUM -> {
                ticket.setPriority(TicketPriority.HIGH);
                yield "Priority escalated from MEDIUM to HIGH (overdue)";
            }
            case HIGH -> {
                ticket.setPriority(TicketPriority.CRITICAL);
                ticket.setOverdue(true);
                yield "Priority escalated from HIGH to CRITICAL (overdue)";
            }
            case CRITICAL -> {
                ticket.setOverdue(true);
                yield "Ticket remains CRITICAL and overdue";
            }
        };

        ticketRepository.save(ticket);
        auditLogService.record(AuditAction.AUTO_ESCALATE, AuditEntityType.TICKET,
                ticket.getId(), null, details);
    }
}
