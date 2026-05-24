package com.att.tdp.issueflow.ticket;

import com.att.tdp.issueflow.BaseIntegrationTest;
import com.att.tdp.issueflow.project.Project;
import com.att.tdp.issueflow.project.ProjectRepository;
import com.att.tdp.issueflow.user.User;
import com.att.tdp.issueflow.user.UserRepository;
import com.att.tdp.issueflow.user.UserRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class TicketEscalationSchedulerTest extends BaseIntegrationTest {

    @Autowired TicketEscalationScheduler escalationScheduler;
    @Autowired TicketRepository ticketRepository;
    @Autowired UserRepository userRepository;
    @Autowired ProjectRepository projectRepository;

    private static final LocalDateTime PAST   = LocalDateTime.now().minusHours(1);
    private static final LocalDateTime FUTURE = LocalDateTime.now().plusHours(1);

    private Project project;

    @BeforeEach
    void setUp() {
        User owner = userRepository.save(User.builder()
                .username("owner").email("owner@test.com").fullName("Owner")
                .role(UserRole.ADMIN).build());
        project = projectRepository.save(Project.builder()
                .name("Test Project").description("desc").owner(owner).build());
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private Ticket savedTicket(TicketPriority priority, TicketStatus status, LocalDateTime dueDate) {
        return ticketRepository.save(Ticket.builder()
                .title("Ticket").priority(priority).status(status)
                .type(TicketType.BUG).project(project).dueDate(dueDate).build());
    }

    private Ticket overdueTicket(TicketPriority priority) {
        return savedTicket(priority, TicketStatus.TODO, PAST);
    }

    private Ticket reload(Ticket t) {
        return ticketRepository.findById(t.getId()).orElseThrow();
    }

    private List<Map<String, Object>> escalationLogsFor(Long ticketId) {
        return jdbcTemplate.queryForList(
                "SELECT * FROM audit_logs WHERE entity_id = ? AND action = 'AUTO_ESCALATE' ORDER BY id",
                ticketId);
    }

    // ── priority escalation rules ─────────────────────────────────────────────

    @Test
    void escalate_lowPriority_becomesMedium_overdueStaysFalse() {
        Ticket ticket = overdueTicket(TicketPriority.LOW);

        escalationScheduler.escalateOverdueTickets();

        Ticket after = reload(ticket);
        assertThat(after.getPriority()).isEqualTo(TicketPriority.MEDIUM);
        assertThat(after.isOverdue()).isFalse();
    }

    @Test
    void escalate_mediumPriority_becomesHigh_overdueStaysFalse() {
        Ticket ticket = overdueTicket(TicketPriority.MEDIUM);

        escalationScheduler.escalateOverdueTickets();

        Ticket after = reload(ticket);
        assertThat(after.getPriority()).isEqualTo(TicketPriority.HIGH);
        assertThat(after.isOverdue()).isFalse();
    }

    @Test
    void escalate_highPriority_becomesCritical_overdueSetTrue() {
        Ticket ticket = overdueTicket(TicketPriority.HIGH);

        escalationScheduler.escalateOverdueTickets();

        Ticket after = reload(ticket);
        assertThat(after.getPriority()).isEqualTo(TicketPriority.CRITICAL);
        assertThat(after.isOverdue()).isTrue();
    }

    @Test
    void escalate_criticalPriority_staysCritical_overdueSetTrue() {
        Ticket ticket = overdueTicket(TicketPriority.CRITICAL);

        escalationScheduler.escalateOverdueTickets();

        Ticket after = reload(ticket);
        assertThat(after.getPriority()).isEqualTo(TicketPriority.CRITICAL);
        assertThat(after.isOverdue()).isTrue();
    }

    // ── eligibility filters ───────────────────────────────────────────────────

    @Test
    void skip_doneTicket_notEscalated() {
        Ticket ticket = savedTicket(TicketPriority.LOW, TicketStatus.DONE, PAST);

        escalationScheduler.escalateOverdueTickets();

        assertThat(reload(ticket).getPriority()).isEqualTo(TicketPriority.LOW);
        assertThat(escalationLogsFor(ticket.getId())).isEmpty();
    }

    @Test
    void skip_softDeletedTicket_notEscalated() {
        Ticket ticket = savedTicket(TicketPriority.LOW, TicketStatus.TODO, PAST);
        ticket.setDeletedAt(LocalDateTime.now());
        ticketRepository.save(ticket);

        escalationScheduler.escalateOverdueTickets();

        assertThat(reload(ticket).getPriority()).isEqualTo(TicketPriority.LOW);
        assertThat(escalationLogsFor(ticket.getId())).isEmpty();
    }

    @Test
    void skip_noDueDateTicket_notEscalated() {
        Ticket ticket = savedTicket(TicketPriority.LOW, TicketStatus.TODO, null);

        escalationScheduler.escalateOverdueTickets();

        assertThat(reload(ticket).getPriority()).isEqualTo(TicketPriority.LOW);
    }

    @Test
    void skip_futureDueDateTicket_notEscalated() {
        Ticket ticket = savedTicket(TicketPriority.LOW, TicketStatus.TODO, FUTURE);

        escalationScheduler.escalateOverdueTickets();

        assertThat(reload(ticket).getPriority()).isEqualTo(TicketPriority.LOW);
    }

    // ── status is never modified ──────────────────────────────────────────────

    @Test
    void escalate_neverModifiesStatus() {
        Ticket inProgress = savedTicket(TicketPriority.LOW,    TicketStatus.IN_PROGRESS, PAST);
        Ticket inReview   = savedTicket(TicketPriority.MEDIUM, TicketStatus.IN_REVIEW,   PAST);

        escalationScheduler.escalateOverdueTickets();

        // Priority should advance (tickets were eligible) …
        assertThat(reload(inProgress).getPriority()).isEqualTo(TicketPriority.MEDIUM);
        assertThat(reload(inReview).getPriority()).isEqualTo(TicketPriority.HIGH);
        // … but status must be untouched.
        assertThat(reload(inProgress).getStatus()).isEqualTo(TicketStatus.IN_PROGRESS);
        assertThat(reload(inReview).getStatus()).isEqualTo(TicketStatus.IN_REVIEW);
    }

    // ── batch processing ──────────────────────────────────────────────────────

    @Test
    void escalate_multipleEligibleTickets_allEscalated() {
        Ticket t1 = overdueTicket(TicketPriority.LOW);
        Ticket t2 = overdueTicket(TicketPriority.MEDIUM);
        Ticket t3 = overdueTicket(TicketPriority.HIGH);

        escalationScheduler.escalateOverdueTickets();

        assertThat(reload(t1).getPriority()).isEqualTo(TicketPriority.MEDIUM);
        assertThat(reload(t2).getPriority()).isEqualTo(TicketPriority.HIGH);
        assertThat(reload(t3).getPriority()).isEqualTo(TicketPriority.CRITICAL);
    }

    // ── idempotency ───────────────────────────────────────────────────────────

    @Test
    void escalate_criticalTicket_safeToRunMultipleTimes() {
        Ticket ticket = overdueTicket(TicketPriority.CRITICAL);

        escalationScheduler.escalateOverdueTickets();
        escalationScheduler.escalateOverdueTickets();

        Ticket after = reload(ticket);
        assertThat(after.getPriority()).isEqualTo(TicketPriority.CRITICAL);
        assertThat(after.isOverdue()).isTrue();
    }

    // ── audit log ─────────────────────────────────────────────────────────────

    @Test
    void escalate_writesAuditLog_systemActorNullPerformedBy() {
        Ticket ticket = overdueTicket(TicketPriority.HIGH);

        escalationScheduler.escalateOverdueTickets();

        List<Map<String, Object>> logs = escalationLogsFor(ticket.getId());
        assertThat(logs).hasSize(1);
        Map<String, Object> log = logs.get(0);
        assertThat(log.get("action")).isEqualTo("AUTO_ESCALATE");
        assertThat(log.get("entity_type")).isEqualTo("TICKET");
        assertThat(log.get("actor")).isEqualTo("SYSTEM");
        assertThat(log.get("performed_by")).isNull();
        assertThat(log.get("entity_id")).isEqualTo(ticket.getId());
    }

    @Test
    void escalate_auditLog_correctDetailsForAllPriorityTransitions() {
        Ticket low      = overdueTicket(TicketPriority.LOW);
        Ticket medium   = overdueTicket(TicketPriority.MEDIUM);
        Ticket high     = overdueTicket(TicketPriority.HIGH);
        Ticket critical = overdueTicket(TicketPriority.CRITICAL);

        escalationScheduler.escalateOverdueTickets();

        assertThat(escalationLogsFor(low.getId()))
                .extracting(m -> m.get("details"))
                .containsExactly("Priority escalated from LOW to MEDIUM (overdue)");
        assertThat(escalationLogsFor(medium.getId()))
                .extracting(m -> m.get("details"))
                .containsExactly("Priority escalated from MEDIUM to HIGH (overdue)");
        assertThat(escalationLogsFor(high.getId()))
                .extracting(m -> m.get("details"))
                .containsExactly("Priority escalated from HIGH to CRITICAL (overdue)");
        assertThat(escalationLogsFor(critical.getId()))
                .extracting(m -> m.get("details"))
                .containsExactly("Ticket remains CRITICAL and overdue");
    }
}
