package com.att.tdp.issueflow.softdelete;

import com.att.tdp.issueflow.BaseIntegrationTest;
import com.att.tdp.issueflow.project.Project;
import com.att.tdp.issueflow.project.ProjectRepository;
import com.att.tdp.issueflow.ticket.*;
import com.att.tdp.issueflow.user.User;
import com.att.tdp.issueflow.user.UserRepository;
import com.att.tdp.issueflow.user.UserRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class SoftDeleteIntegrationTest extends BaseIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired UserRepository userRepository;
    @Autowired ProjectRepository projectRepository;
    @Autowired TicketRepository ticketRepository;

    private Long projectId;
    private Long ticketId;

    @BeforeEach
    void setUp() {
        User owner = userRepository.save(User.builder()
                .username("owner").email("owner@test.com").fullName("Owner").role(UserRole.ADMIN).build());

        Project project = projectRepository.save(Project.builder()
                .name("Test Project").description("desc").owner(owner).build());

        Ticket ticket = ticketRepository.save(Ticket.builder()
                .title("Test Ticket").priority(TicketPriority.MEDIUM).type(TicketType.BUG)
                .project(project).build());

        projectId = project.getId();
        ticketId = ticket.getId();
    }

    // ── Soft-deleted ticket visibility ───────────────────────────────────────

    @Test
    @WithMockUser(roles = "DEVELOPER")
    void softDeletedTicket_doesNotAppearInGetByProject() throws Exception {
        mockMvc.perform(get("/tickets?projectId=" + projectId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)));

        mockMvc.perform(delete("/tickets/" + ticketId)).andExpect(status().isOk());

        mockMvc.perform(get("/tickets?projectId=" + projectId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    @WithMockUser(roles = "DEVELOPER")
    void developer_softDeletesTicket_returns200() throws Exception {
        mockMvc.perform(delete("/tickets/" + ticketId))
                .andExpect(status().isOk());
    }

    // ── Access control ────────────────────────────────────────────────────────

    @Test
    @WithMockUser(roles = "ADMIN")
    void getDeletedTickets_adminRole_returns200() throws Exception {
        mockMvc.perform(get("/tickets/deleted?projectId=" + projectId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }

    @Test
    @WithMockUser(roles = "DEVELOPER")
    void getDeletedTickets_developerRole_returns403() throws Exception {
        mockMvc.perform(get("/tickets/deleted?projectId=" + projectId))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void getDeletedProjects_adminRole_returns200() throws Exception {
        mockMvc.perform(get("/projects/deleted"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }

    @Test
    @WithMockUser(roles = "DEVELOPER")
    void getDeletedProjects_developerRole_returns403() throws Exception {
        mockMvc.perform(get("/projects/deleted"))
                .andExpect(status().isForbidden());
    }

    // ── Cascade delete ────────────────────────────────────────────────────────

    @Test
    @WithMockUser(roles = "ADMIN")
    void deleteProject_cascadesToActiveTickets() throws Exception {
        mockMvc.perform(delete("/projects/" + projectId))
                .andExpect(status().isOk());

        Project project = projectRepository.findById(projectId).orElseThrow();
        assertThat(project.getDeletedAt()).isNotNull();

        Ticket ticket = ticketRepository.findById(ticketId).orElseThrow();
        assertThat(ticket.getDeletedAt()).isNotNull();
        assertThat(ticket.getDeletedAt()).isEqualTo(project.getDeletedAt());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void deleteProject_doesNotTouchAlreadyDeletedTickets() throws Exception {
        // PostgreSQL stores timestamps with microsecond precision — truncate nanoseconds
        LocalDateTime before = LocalDateTime.now().minusSeconds(1).truncatedTo(ChronoUnit.MICROS);
        Ticket ticket = ticketRepository.findById(ticketId).orElseThrow();
        ticket.setDeletedAt(before);
        ticketRepository.save(ticket);

        mockMvc.perform(delete("/projects/" + projectId))
                .andExpect(status().isOk());

        Ticket afterDelete = ticketRepository.findById(ticketId).orElseThrow();
        Project project = projectRepository.findById(projectId).orElseThrow();
        assertThat(afterDelete.getDeletedAt()).isNotEqualTo(project.getDeletedAt());
        assertThat(afterDelete.getDeletedAt()).isEqualTo(before);
    }

    // ── Cascade restore ───────────────────────────────────────────────────────

    @Test
    @WithMockUser(roles = "ADMIN")
    void restoreProject_restoresCascadeDeletedTickets() throws Exception {
        mockMvc.perform(delete("/projects/" + projectId)).andExpect(status().isOk());
        mockMvc.perform(post("/projects/" + projectId + "/restore")).andExpect(status().isOk());

        assertThat(projectRepository.findById(projectId).orElseThrow().getDeletedAt()).isNull();
        assertThat(ticketRepository.findById(ticketId).orElseThrow().getDeletedAt()).isNull();
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void restoreProject_doesNotRestoreIndividuallyDeletedTickets() throws Exception {
        Ticket ticket = ticketRepository.findById(ticketId).orElseThrow();
        ticket.setDeletedAt(LocalDateTime.now().minusSeconds(5).truncatedTo(ChronoUnit.MICROS));
        ticketRepository.save(ticket);
        LocalDateTime individualTimestamp = ticket.getDeletedAt();

        mockMvc.perform(delete("/projects/" + projectId)).andExpect(status().isOk());
        mockMvc.perform(post("/projects/" + projectId + "/restore")).andExpect(status().isOk());

        assertThat(projectRepository.findById(projectId).orElseThrow().getDeletedAt()).isNull();

        Ticket afterRestore = ticketRepository.findById(ticketId).orElseThrow();
        assertThat(afterRestore.getDeletedAt()).isEqualTo(individualTimestamp);
    }

    @Test
    @WithMockUser(roles = "DEVELOPER")
    void restoreProject_developerRole_returns403() throws Exception {
        mockMvc.perform(post("/projects/" + projectId + "/restore"))
                .andExpect(status().isForbidden());
    }

    // ── Ticket restore ────────────────────────────────────────────────────────

    @Test
    @WithMockUser(roles = "ADMIN")
    void restoreTicket_whenProjectIsActive_returns200() throws Exception {
        mockMvc.perform(delete("/tickets/" + ticketId)).andExpect(status().isOk());
        mockMvc.perform(post("/tickets/" + ticketId + "/restore")).andExpect(status().isOk());

        assertThat(ticketRepository.findById(ticketId).orElseThrow().getDeletedAt()).isNull();
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void restoreTicket_whenParentProjectIsDeleted_returns400() throws Exception {
        mockMvc.perform(delete("/projects/" + projectId)).andExpect(status().isOk());

        mockMvc.perform(post("/tickets/" + ticketId + "/restore"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Cannot restore ticket: parent project is deleted"));
    }

    @Test
    @WithMockUser(roles = "DEVELOPER")
    void restoreTicket_developerRole_returns403() throws Exception {
        mockMvc.perform(post("/tickets/" + ticketId + "/restore"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void restoreProject_whenProjectNotDeleted_returns404() throws Exception {
        mockMvc.perform(post("/projects/" + projectId + "/restore"))
                .andExpect(status().isNotFound());
    }
}
