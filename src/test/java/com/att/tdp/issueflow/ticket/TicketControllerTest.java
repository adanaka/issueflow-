package com.att.tdp.issueflow.ticket;

import com.att.tdp.issueflow.BaseIntegrationTest;
import com.att.tdp.issueflow.auth.dto.LoginRequest;
import com.att.tdp.issueflow.project.Project;
import com.att.tdp.issueflow.project.ProjectRepository;
import com.att.tdp.issueflow.ticket.dto.CreateTicketRequest;
import com.att.tdp.issueflow.ticket.dto.UpdateTicketRequest;
import com.att.tdp.issueflow.user.User;
import com.att.tdp.issueflow.user.UserRepository;
import com.att.tdp.issueflow.user.UserRole;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class TicketControllerTest extends BaseIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired UserRepository userRepository;
    @Autowired ProjectRepository projectRepository;
    @Autowired TicketRepository ticketRepository;
    @Autowired TicketDependencyRepository ticketDependencyRepository;
    @Autowired PasswordEncoder passwordEncoder;

    private String adminToken;
    private String devToken;
    private Long projectId;

    @BeforeEach
    void setUp() throws Exception {
        User admin = userRepository.save(User.builder()
                .username("admin").email("admin@test.com").fullName("Admin User")
                .role(UserRole.ADMIN).password(passwordEncoder.encode("adminpass")).build());

        userRepository.save(User.builder()
                .username("dev").email("dev@test.com").fullName("Developer")
                .role(UserRole.DEVELOPER).password(passwordEncoder.encode("devpass")).build());

        Project project = projectRepository.save(Project.builder()
                .name("Test Project").description("Test description").owner(admin).build());
        projectId = project.getId();

        adminToken = login("admin", "adminpass");
        devToken = login("dev", "devpass");
    }

    private String login(String username, String password) throws Exception {
        LoginRequest req = new LoginRequest();
        req.setUsername(username);
        req.setPassword(password);

        String body = mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        return objectMapper.readTree(body).get("accessToken").asText();
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }

    private Long createTicket(String title, TicketStatus status) throws Exception {
        CreateTicketRequest req = new CreateTicketRequest();
        req.setTitle(title);
        req.setStatus(status);
        req.setPriority(TicketPriority.MEDIUM);
        req.setType(TicketType.BUG);
        req.setProjectId(projectId);

        String body = mockMvc.perform(post("/tickets")
                        .header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        return objectMapper.readTree(body).get("id").asLong();
    }

    // ── CREATE ────────────────────────────────────────────────────────────────

    @Test
    void createTicket_valid_returns200WithId() throws Exception {
        CreateTicketRequest req = new CreateTicketRequest();
        req.setTitle("My Ticket");
        req.setStatus(TicketStatus.TODO);
        req.setPriority(TicketPriority.HIGH);
        req.setType(TicketType.FEATURE);
        req.setProjectId(projectId);

        mockMvc.perform(post("/tickets")
                        .header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.title").value("My Ticket"))
                .andExpect(jsonPath("$.status").value("TODO"));
    }

    @Test
    void createTicket_missingTitle_returns400() throws Exception {
        CreateTicketRequest req = new CreateTicketRequest();
        req.setStatus(TicketStatus.TODO);
        req.setPriority(TicketPriority.MEDIUM);
        req.setType(TicketType.BUG);
        req.setProjectId(projectId);

        mockMvc.perform(post("/tickets")
                        .header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.title").exists());
    }

    @Test
    void createTicket_missingStatus_returns400() throws Exception {
        CreateTicketRequest req = new CreateTicketRequest();
        req.setTitle("Missing status");
        req.setPriority(TicketPriority.MEDIUM);
        req.setType(TicketType.BUG);
        req.setProjectId(projectId);

        mockMvc.perform(post("/tickets")
                        .header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.status").exists());
    }

    @Test
    void createTicket_nonExistentProject_returns404() throws Exception {
        CreateTicketRequest req = new CreateTicketRequest();
        req.setTitle("Orphan Ticket");
        req.setStatus(TicketStatus.TODO);
        req.setPriority(TicketPriority.LOW);
        req.setType(TicketType.BUG);
        req.setProjectId(9999L);

        mockMvc.perform(post("/tickets")
                        .header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value(containsString("9999")));
    }

    @Test
    void createTicket_invalidEnumValue_returns400() throws Exception {
        String rawJson = "{\"title\":\"T\",\"status\":\"INVALID\",\"priority\":\"MEDIUM\"," +
                "\"type\":\"BUG\",\"projectId\":" + projectId + "}";

        mockMvc.perform(post("/tickets")
                        .header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(rawJson))
                .andExpect(status().isBadRequest());
    }

    // ── READ ──────────────────────────────────────────────────────────────────

    @Test
    void getTicketById_existingTicket_returns200() throws Exception {
        Long ticketId = createTicket("Readable Ticket", TicketStatus.TODO);

        mockMvc.perform(get("/tickets/" + ticketId)
                        .header("Authorization", bearer(devToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(ticketId))
                .andExpect(jsonPath("$.title").value("Readable Ticket"));
    }

    @Test
    void getTicketById_notFound_returns404() throws Exception {
        mockMvc.perform(get("/tickets/9999")
                        .header("Authorization", bearer(devToken)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value(containsString("9999")));
    }

    @Test
    void listTicketsByProject_returnsActiveTickets() throws Exception {
        createTicket("Ticket A", TicketStatus.TODO);
        createTicket("Ticket B", TicketStatus.TODO);

        mockMvc.perform(get("/tickets?projectId=" + projectId)
                        .header("Authorization", bearer(devToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)));
    }

    // ── UPDATE – valid transitions ─────────────────────────────────────────────

    @Test
    void updateTicket_todoToInProgress_returns200() throws Exception {
        Long ticketId = createTicket("Transition Ticket", TicketStatus.TODO);

        UpdateTicketRequest req = new UpdateTicketRequest();
        req.setStatus(TicketStatus.IN_PROGRESS);

        mockMvc.perform(put("/tickets/" + ticketId)
                        .header("Authorization", bearer(devToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("IN_PROGRESS"));
    }

    @Test
    void updateTicket_inProgressToInReview_returns200() throws Exception {
        Long ticketId = createTicket("Review Ticket", TicketStatus.TODO);

        UpdateTicketRequest toInProgress = new UpdateTicketRequest();
        toInProgress.setStatus(TicketStatus.IN_PROGRESS);
        mockMvc.perform(put("/tickets/" + ticketId)
                .header("Authorization", bearer(devToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(toInProgress)));

        UpdateTicketRequest toInReview = new UpdateTicketRequest();
        toInReview.setStatus(TicketStatus.IN_REVIEW);
        mockMvc.perform(put("/tickets/" + ticketId)
                        .header("Authorization", bearer(devToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(toInReview)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("IN_REVIEW"));
    }

    @Test
    void updateTicket_inReviewToDone_noBlockers_returns200() throws Exception {
        Long ticketId = createTicket("Done Ticket", TicketStatus.TODO);

        for (TicketStatus next : new TicketStatus[]{TicketStatus.IN_PROGRESS, TicketStatus.IN_REVIEW}) {
            UpdateTicketRequest req = new UpdateTicketRequest();
            req.setStatus(next);
            mockMvc.perform(put("/tickets/" + ticketId)
                    .header("Authorization", bearer(devToken))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(req)));
        }

        UpdateTicketRequest toDone = new UpdateTicketRequest();
        toDone.setStatus(TicketStatus.DONE);
        mockMvc.perform(put("/tickets/" + ticketId)
                        .header("Authorization", bearer(devToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(toDone)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DONE"));
    }

    // ── UPDATE – invalid transitions ──────────────────────────────────────────

    @Test
    void updateTicket_skipTransition_todoToDone_returns400() throws Exception {
        Long ticketId = createTicket("Skip Ticket", TicketStatus.TODO);

        UpdateTicketRequest req = new UpdateTicketRequest();
        req.setStatus(TicketStatus.DONE);

        mockMvc.perform(put("/tickets/" + ticketId)
                        .header("Authorization", bearer(devToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void updateTicket_backwardTransition_returns400() throws Exception {
        Long ticketId = createTicket("Backward Ticket", TicketStatus.TODO);

        UpdateTicketRequest toInProgress = new UpdateTicketRequest();
        toInProgress.setStatus(TicketStatus.IN_PROGRESS);
        mockMvc.perform(put("/tickets/" + ticketId)
                .header("Authorization", bearer(devToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(toInProgress)));

        UpdateTicketRequest backToTodo = new UpdateTicketRequest();
        backToTodo.setStatus(TicketStatus.TODO);
        mockMvc.perform(put("/tickets/" + ticketId)
                        .header("Authorization", bearer(devToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(backToTodo)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void updateTicket_doneTicket_anyUpdate_returns400WithDone() throws Exception {
        Long ticketId = createTicket("Done Lock Ticket", TicketStatus.TODO);

        for (TicketStatus next : new TicketStatus[]{TicketStatus.IN_PROGRESS, TicketStatus.IN_REVIEW, TicketStatus.DONE}) {
            UpdateTicketRequest req = new UpdateTicketRequest();
            req.setStatus(next);
            mockMvc.perform(put("/tickets/" + ticketId)
                    .header("Authorization", bearer(devToken))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(req)));
        }

        UpdateTicketRequest req = new UpdateTicketRequest();
        req.setTitle("New Title After Done");
        mockMvc.perform(put("/tickets/" + ticketId)
                        .header("Authorization", bearer(devToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString("DONE")));
    }

    // ── Unresolved blockers ───────────────────────────────────────────────────

    @Test
    void updateTicket_toDone_unresolvedBlocker_returns422() throws Exception {
        Long ticketId = createTicket("Blocked Ticket", TicketStatus.TODO);
        Long blockerId = createTicket("Blocker", TicketStatus.TODO);

        for (TicketStatus s : new TicketStatus[]{TicketStatus.IN_PROGRESS, TicketStatus.IN_REVIEW}) {
            UpdateTicketRequest req = new UpdateTicketRequest();
            req.setStatus(s);
            mockMvc.perform(put("/tickets/" + ticketId)
                    .header("Authorization", bearer(devToken))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(req)));
        }

        Ticket ticket = ticketRepository.findById(ticketId).orElseThrow();
        Ticket blocker = ticketRepository.findById(blockerId).orElseThrow();
        ticketDependencyRepository.save(TicketDependency.builder()
                .id(new TicketDependencyId(ticketId, blockerId))
                .ticket(ticket).blockedBy(blocker).build());

        UpdateTicketRequest toDone = new UpdateTicketRequest();
        toDone.setStatus(TicketStatus.DONE);
        mockMvc.perform(put("/tickets/" + ticketId)
                        .header("Authorization", bearer(devToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(toDone)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.message").value(containsString("unresolved blockers")));
    }

    // ── DELETE / soft-delete ──────────────────────────────────────────────────

    @Test
    void deleteTicket_existingTicket_returns200_thenGetReturns404() throws Exception {
        Long ticketId = createTicket("Delete Me", TicketStatus.TODO);

        mockMvc.perform(delete("/tickets/" + ticketId)
                        .header("Authorization", bearer(devToken)))
                .andExpect(status().isOk());

        mockMvc.perform(get("/tickets/" + ticketId)
                        .header("Authorization", bearer(devToken)))
                .andExpect(status().isNotFound());
    }

    @Test
    void deleteTicket_notFound_returns404() throws Exception {
        mockMvc.perform(delete("/tickets/9999")
                        .header("Authorization", bearer(adminToken)))
                .andExpect(status().isNotFound());
    }

    @Test
    void getDeletedTickets_adminRole_includesSoftDeleted() throws Exception {
        Long ticketId = createTicket("Soft Deleted", TicketStatus.TODO);
        mockMvc.perform(delete("/tickets/" + ticketId)
                .header("Authorization", bearer(devToken)));

        mockMvc.perform(get("/tickets/deleted?projectId=" + projectId)
                        .header("Authorization", bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(greaterThanOrEqualTo(1))));
    }

    @Test
    void getDeletedTickets_developerRole_returns403() throws Exception {
        mockMvc.perform(get("/tickets/deleted?projectId=" + projectId)
                        .header("Authorization", bearer(devToken)))
                .andExpect(status().isForbidden());
    }

    @Test
    void restoreTicket_adminRole_returns200() throws Exception {
        Long ticketId = createTicket("Restore Me", TicketStatus.TODO);
        mockMvc.perform(delete("/tickets/" + ticketId)
                .header("Authorization", bearer(devToken)));

        mockMvc.perform(post("/tickets/" + ticketId + "/restore")
                        .header("Authorization", bearer(adminToken)))
                .andExpect(status().isOk());

        mockMvc.perform(get("/tickets/" + ticketId)
                        .header("Authorization", bearer(devToken)))
                .andExpect(status().isOk());
    }

    @Test
    void restoreTicket_developerRole_returns403() throws Exception {
        Long ticketId = createTicket("Forbidden Restore", TicketStatus.TODO);
        mockMvc.perform(delete("/tickets/" + ticketId)
                .header("Authorization", bearer(devToken)));

        mockMvc.perform(post("/tickets/" + ticketId + "/restore")
                        .header("Authorization", bearer(devToken)))
                .andExpect(status().isForbidden());
    }

    // ── Auth ──────────────────────────────────────────────────────────────────

    @Test
    void createTicket_noToken_returns401() throws Exception {
        CreateTicketRequest req = new CreateTicketRequest();
        req.setTitle("Unauthorized");
        req.setStatus(TicketStatus.TODO);
        req.setPriority(TicketPriority.LOW);
        req.setType(TicketType.BUG);
        req.setProjectId(projectId);

        mockMvc.perform(post("/tickets")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void getTickets_noToken_returns401() throws Exception {
        mockMvc.perform(get("/tickets?projectId=" + projectId))
                .andExpect(status().isUnauthorized());
    }

    // ── Optimistic locking ────────────────────────────────────────────────────

    @Test
    void optimisticLocking_staleEntitySave_throwsException() throws Exception {
        Long ticketId = createTicket("Locked Ticket", TicketStatus.TODO);

        Ticket staleTicket = ticketRepository.findById(ticketId).orElseThrow();

        UpdateTicketRequest req = new UpdateTicketRequest();
        req.setTitle("Updated via HTTP");
        mockMvc.perform(put("/tickets/" + ticketId)
                        .header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk());

        staleTicket.setTitle("Stale update");
        assertThatThrownBy(() -> ticketRepository.saveAndFlush(staleTicket))
                .isInstanceOf(ObjectOptimisticLockingFailureException.class);
    }
}
