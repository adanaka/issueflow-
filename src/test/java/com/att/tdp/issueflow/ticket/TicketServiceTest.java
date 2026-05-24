package com.att.tdp.issueflow.ticket;

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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TicketServiceTest {

    @Mock TicketRepository ticketRepository;
    @Mock TicketDependencyRepository ticketDependencyRepository;
    @Mock ProjectRepository projectRepository;
    @Mock UserRepository userRepository;
    @Mock AuditLogService auditLogService;
    @Mock SecurityUtils securityUtils;

    @InjectMocks TicketServiceImpl ticketService;

    // ── Helpers ───────────────────────────────────────────────────────────────

    private Project project() {
        return Project.builder().id(10L).name("P").description("d").build();
    }

    private Ticket ticket(TicketStatus status) {
        return Ticket.builder()
                .id(1L).title("T").status(status)
                .priority(TicketPriority.MEDIUM).type(TicketType.BUG)
                .project(project()).version(0L)
                .build();
    }

    private UpdateTicketRequest statusReq(TicketStatus next) {
        UpdateTicketRequest r = new UpdateTicketRequest();
        r.setStatus(next);
        return r;
    }

    private CreateTicketRequest createReq(Long projectId, Long assigneeId) {
        CreateTicketRequest r = new CreateTicketRequest();
        r.setTitle("New Ticket");
        r.setStatus(TicketStatus.TODO);
        r.setPriority(TicketPriority.HIGH);
        r.setType(TicketType.BUG);
        r.setProjectId(projectId);
        r.setAssigneeId(assigneeId);
        return r;
    }

    // ── createTicket – projectId validation ───────────────────────────────────

    @Test
    void createTicket_nonExistentProject_throwsResourceNotFound() {
        when(projectRepository.findByIdAndDeletedAtIsNull(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> ticketService.createTicket(createReq(99L, null)))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("99");
    }

    // ── createTicket – assigneeId validation ──────────────────────────────────

    @Test
    void createTicket_nonExistentAssignee_throwsResourceNotFound() {
        when(projectRepository.findByIdAndDeletedAtIsNull(1L)).thenReturn(Optional.of(project()));
        when(userRepository.findById(55L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> ticketService.createTicket(createReq(1L, 55L)))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("55");
    }

    @Test
    void createTicket_validAssignee_setsAssigneeOnResponse() {
        Project p = project();
        User assignee = User.builder().id(2L).username("dev").email("d@d.com").fullName("Dev").build();
        Ticket saved = Ticket.builder().id(5L).title("New Ticket").status(TicketStatus.TODO)
                .priority(TicketPriority.HIGH).type(TicketType.BUG).project(p).assignee(assignee).build();

        when(projectRepository.findByIdAndDeletedAtIsNull(1L)).thenReturn(Optional.of(p));
        when(userRepository.findById(2L)).thenReturn(Optional.of(assignee));
        when(ticketRepository.save(any())).thenReturn(saved);

        TicketResponse response = ticketService.createTicket(createReq(1L, 2L));

        assertThat(response.getAssigneeId()).isEqualTo(2L);
    }

    @Test
    void createTicket_nullAssignee_createsTicketWithoutAssignee() {
        Project p = project();
        Ticket saved = Ticket.builder().id(5L).title("New Ticket").status(TicketStatus.TODO)
                .priority(TicketPriority.HIGH).type(TicketType.BUG).project(p).build();

        when(projectRepository.findByIdAndDeletedAtIsNull(1L)).thenReturn(Optional.of(p));
        when(ticketRepository.save(any())).thenReturn(saved);

        TicketResponse response = ticketService.createTicket(createReq(1L, null));

        assertThat(response.getAssigneeId()).isNull();
    }

    // ── DONE lock ─────────────────────────────────────────────────────────────

    @Test
    void updateTicket_doneTicket_anyField_throwsBadRequest_withDoneInMessage() {
        when(ticketRepository.findByIdAndDeletedAtIsNull(1L))
                .thenReturn(Optional.of(ticket(TicketStatus.DONE)));

        // Attempt any update on a DONE ticket
        UpdateTicketRequest req = new UpdateTicketRequest();
        req.setTitle("new title");

        assertThatThrownBy(() -> ticketService.updateTicket(1L, req))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("DONE");
    }

    // ── Valid status transitions ──────────────────────────────────────────────

    @Test
    void updateTicket_todoToInProgress_isValid() {
        Ticket t = ticket(TicketStatus.TODO);
        when(ticketRepository.findByIdAndDeletedAtIsNull(1L)).thenReturn(Optional.of(t));
        when(ticketRepository.save(any())).thenReturn(t);

        assertThatNoException().isThrownBy(
                () -> ticketService.updateTicket(1L, statusReq(TicketStatus.IN_PROGRESS)));
    }

    @Test
    void updateTicket_inProgressToInReview_isValid() {
        Ticket t = ticket(TicketStatus.IN_PROGRESS);
        when(ticketRepository.findByIdAndDeletedAtIsNull(1L)).thenReturn(Optional.of(t));
        when(ticketRepository.save(any())).thenReturn(t);

        assertThatNoException().isThrownBy(
                () -> ticketService.updateTicket(1L, statusReq(TicketStatus.IN_REVIEW)));
    }

    @Test
    void updateTicket_inReviewToDone_allBlockersDone_isValid() {
        Ticket t = ticket(TicketStatus.IN_REVIEW);
        when(ticketRepository.findByIdAndDeletedAtIsNull(1L)).thenReturn(Optional.of(t));
        when(ticketDependencyRepository.findUnresolvedBlockers(1L)).thenReturn(List.of());
        when(ticketRepository.save(any())).thenReturn(t);

        assertThatNoException().isThrownBy(
                () -> ticketService.updateTicket(1L, statusReq(TicketStatus.DONE)));
    }

    // ── Invalid status transitions ────────────────────────────────────────────

    @Test
    void updateTicket_todoToDone_skipTransition_throwsBadRequest() {
        when(ticketRepository.findByIdAndDeletedAtIsNull(1L))
                .thenReturn(Optional.of(ticket(TicketStatus.TODO)));

        assertThatThrownBy(() -> ticketService.updateTicket(1L, statusReq(TicketStatus.DONE)))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void updateTicket_todoToInReview_skipTransition_throwsBadRequest() {
        when(ticketRepository.findByIdAndDeletedAtIsNull(1L))
                .thenReturn(Optional.of(ticket(TicketStatus.TODO)));

        assertThatThrownBy(() -> ticketService.updateTicket(1L, statusReq(TicketStatus.IN_REVIEW)))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void updateTicket_inProgressToTodo_backward_throwsBadRequest() {
        when(ticketRepository.findByIdAndDeletedAtIsNull(1L))
                .thenReturn(Optional.of(ticket(TicketStatus.IN_PROGRESS)));

        assertThatThrownBy(() -> ticketService.updateTicket(1L, statusReq(TicketStatus.TODO)))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void updateTicket_inReviewToInProgress_backward_throwsBadRequest() {
        when(ticketRepository.findByIdAndDeletedAtIsNull(1L))
                .thenReturn(Optional.of(ticket(TicketStatus.IN_REVIEW)));

        assertThatThrownBy(() -> ticketService.updateTicket(1L, statusReq(TicketStatus.IN_PROGRESS)))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void updateTicket_doneToAnything_throwsBadRequest_caughtByDoneLock() {
        when(ticketRepository.findByIdAndDeletedAtIsNull(1L))
                .thenReturn(Optional.of(ticket(TicketStatus.DONE)));

        // The early DONE-lock check fires before validateStatusTransition
        assertThatThrownBy(() -> ticketService.updateTicket(1L, statusReq(TicketStatus.IN_REVIEW)))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("DONE");
    }

    // ── Unresolved blockers ───────────────────────────────────────────────────

    @Test
    void updateTicket_toDone_unresolvedBlocker_throwsBusinessRule() {
        Ticket blocker = Ticket.builder().id(2L).status(TicketStatus.IN_PROGRESS).build();
        when(ticketRepository.findByIdAndDeletedAtIsNull(1L))
                .thenReturn(Optional.of(ticket(TicketStatus.IN_REVIEW)));
        when(ticketDependencyRepository.findUnresolvedBlockers(1L)).thenReturn(List.of(blocker));

        assertThatThrownBy(() -> ticketService.updateTicket(1L, statusReq(TicketStatus.DONE)))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("unresolved blockers");
    }

    @Test
    void updateTicket_toDone_softDeletedBlockerNotDone_stillBlocks() {
        // findUnresolvedBlockers does not filter by deletedAt — soft-deleted IN_PROGRESS tickets still block
        Ticket softDeletedBlocker = Ticket.builder()
                .id(2L).status(TicketStatus.IN_PROGRESS)
                .deletedAt(LocalDateTime.now().minusDays(1))
                .build();
        when(ticketRepository.findByIdAndDeletedAtIsNull(1L))
                .thenReturn(Optional.of(ticket(TicketStatus.IN_REVIEW)));
        when(ticketDependencyRepository.findUnresolvedBlockers(1L)).thenReturn(List.of(softDeletedBlocker));

        assertThatThrownBy(() -> ticketService.updateTicket(1L, statusReq(TicketStatus.DONE)))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("unresolved blockers");
    }
}
