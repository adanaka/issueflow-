package com.att.tdp.issueflow.ticket;

import com.att.tdp.issueflow.BaseIntegrationTest;
import com.att.tdp.issueflow.auth.dto.LoginRequest;
import com.att.tdp.issueflow.project.Project;
import com.att.tdp.issueflow.project.ProjectRepository;
import com.att.tdp.issueflow.ticket.dto.AddDependencyRequest;
import com.att.tdp.issueflow.ticket.dto.UpdateTicketRequest;
import com.att.tdp.issueflow.user.User;
import com.att.tdp.issueflow.user.UserRepository;
import com.att.tdp.issueflow.user.UserRole;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class TicketDependencyControllerTest extends BaseIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired UserRepository userRepository;
    @Autowired ProjectRepository projectRepository;
    @Autowired TicketRepository ticketRepository;
    @Autowired PasswordEncoder passwordEncoder;

    private Long adminId;
    private String adminToken;
    private Long projectId;
    private Long ticketAId;
    private Long ticketBId;
    private Long ticketCId; // belongs to a different project

    @BeforeEach
    void setUp() throws Exception {
        User admin = userRepository.save(User.builder()
                .username("admin").email("admin@test.com").fullName("Admin")
                .role(UserRole.ADMIN).password(passwordEncoder.encode("pass")).build());
        adminId = admin.getId();

        Project project = projectRepository.save(Project.builder()
                .name("Project").description("d").owner(admin).build());
        projectId = project.getId();

        Project otherProject = projectRepository.save(Project.builder()
                .name("Other Project").description("d").owner(admin).build());

        Ticket ticketA = ticketRepository.save(Ticket.builder()
                .title("Ticket A").priority(TicketPriority.MEDIUM)
                .status(TicketStatus.TODO).type(TicketType.BUG)
                .project(project).build());
        ticketAId = ticketA.getId();

        Ticket ticketB = ticketRepository.save(Ticket.builder()
                .title("Ticket B").priority(TicketPriority.MEDIUM)
                .status(TicketStatus.TODO).type(TicketType.BUG)
                .project(project).build());
        ticketBId = ticketB.getId();

        Ticket ticketC = ticketRepository.save(Ticket.builder()
                .title("Ticket C").priority(TicketPriority.MEDIUM)
                .status(TicketStatus.TODO).type(TicketType.BUG)
                .project(otherProject).build());
        ticketCId = ticketC.getId();

        adminToken = login("admin", "pass");
    }

    // ── helpers ───────────────────────────────────────────────────────────────

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

    private String bearer() { return "Bearer " + adminToken; }

    private void addDep(Long ticketId, Long blockerId) throws Exception {
        AddDependencyRequest req = new AddDependencyRequest();
        req.setBlockedBy(blockerId);
        mockMvc.perform(post("/tickets/" + ticketId + "/dependencies")
                        .header("Authorization", bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk());
    }

    private void advanceStatus(Long ticketId, TicketStatus newStatus) throws Exception {
        UpdateTicketRequest req = new UpdateTicketRequest();
        req.setStatus(newStatus);
        mockMvc.perform(put("/tickets/" + ticketId)
                        .header("Authorization", bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk());
    }

    // TODO → IN_PROGRESS → IN_REVIEW
    private void advanceToInReview(Long ticketId) throws Exception {
        advanceStatus(ticketId, TicketStatus.IN_PROGRESS);
        advanceStatus(ticketId, TicketStatus.IN_REVIEW);
    }

    // TODO → IN_PROGRESS → IN_REVIEW → DONE
    private void advanceToDone(Long ticketId) throws Exception {
        advanceStatus(ticketId, TicketStatus.IN_PROGRESS);
        advanceStatus(ticketId, TicketStatus.IN_REVIEW);
        advanceStatus(ticketId, TicketStatus.DONE);
    }

    // ── add dependency ────────────────────────────────────────────────────────

    @Test
    void addDependency_success_returns200_blockerAppearsInList() throws Exception {
        AddDependencyRequest req = new AddDependencyRequest();
        req.setBlockedBy(ticketBId);

        mockMvc.perform(post("/tickets/" + ticketAId + "/dependencies")
                        .header("Authorization", bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk());

        mockMvc.perform(get("/tickets/" + ticketAId + "/dependencies")
                        .header("Authorization", bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].id").value(ticketBId));
    }

    @Test
    void addDependency_nonExistentTicket_returns404() throws Exception {
        AddDependencyRequest req = new AddDependencyRequest();
        req.setBlockedBy(ticketBId);

        mockMvc.perform(post("/tickets/9999/dependencies")
                        .header("Authorization", bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isNotFound());
    }

    @Test
    void addDependency_nonExistentBlocker_returns404() throws Exception {
        AddDependencyRequest req = new AddDependencyRequest();
        req.setBlockedBy(9999L);

        mockMvc.perform(post("/tickets/" + ticketAId + "/dependencies")
                        .header("Authorization", bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isNotFound());
    }

    @Test
    void addDependency_selfDependency_returns400_withMessage() throws Exception {
        AddDependencyRequest req = new AddDependencyRequest();
        req.setBlockedBy(ticketAId);

        mockMvc.perform(post("/tickets/" + ticketAId + "/dependencies")
                        .header("Authorization", bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("A ticket cannot depend on itself"));
    }

    @Test
    void addDependency_crossProject_returns400_withMessage() throws Exception {
        AddDependencyRequest req = new AddDependencyRequest();
        req.setBlockedBy(ticketCId);

        mockMvc.perform(post("/tickets/" + ticketAId + "/dependencies")
                        .header("Authorization", bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Both tickets must belong to the same project"));
    }

    @Test
    void addDependency_duplicate_returns409_withMessage() throws Exception {
        addDep(ticketAId, ticketBId);

        AddDependencyRequest req = new AddDependencyRequest();
        req.setBlockedBy(ticketBId);

        mockMvc.perform(post("/tickets/" + ticketAId + "/dependencies")
                        .header("Authorization", bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Dependency already exists"));
    }

    // ── get dependencies ──────────────────────────────────────────────────────

    @Test
    void getDependencies_afterAdd_containsBlockerWithCorrectShape() throws Exception {
        addDep(ticketAId, ticketBId);

        mockMvc.perform(get("/tickets/" + ticketAId + "/dependencies")
                        .header("Authorization", bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].id").value(ticketBId))
                .andExpect(jsonPath("$[0].title").value("Ticket B"))
                .andExpect(jsonPath("$[0].status").value("TODO"))
                .andExpect(jsonPath("$[0].priority").value("MEDIUM"))
                .andExpect(jsonPath("$[0].projectId").value(projectId))
                .andExpect(jsonPath("$[0].deletedAt").doesNotExist())
                .andExpect(jsonPath("$[0].storagePath").doesNotExist());
    }

    @Test
    void getDependencies_noDependencies_returnsEmptyList() throws Exception {
        mockMvc.perform(get("/tickets/" + ticketAId + "/dependencies")
                        .header("Authorization", bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void getDependencies_nonExistentTicket_returns404() throws Exception {
        mockMvc.perform(get("/tickets/9999/dependencies")
                        .header("Authorization", bearer()))
                .andExpect(status().isNotFound());
    }

    // ── remove dependency ─────────────────────────────────────────────────────

    @Test
    void removeDependency_success_returns200_blockerNoLongerListed() throws Exception {
        addDep(ticketAId, ticketBId);

        mockMvc.perform(delete("/tickets/" + ticketAId + "/dependencies/" + ticketBId)
                        .header("Authorization", bearer()))
                .andExpect(status().isOk());

        mockMvc.perform(get("/tickets/" + ticketAId + "/dependencies")
                        .header("Authorization", bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void removeDependency_nonExistentBlocker_returns404() throws Exception {
        mockMvc.perform(delete("/tickets/" + ticketAId + "/dependencies/9999")
                        .header("Authorization", bearer()))
                .andExpect(status().isNotFound());
    }

    @Test
    void removeDependency_nonExistentTicket_returns404() throws Exception {
        mockMvc.perform(delete("/tickets/9999/dependencies/" + ticketBId)
                        .header("Authorization", bearer()))
                .andExpect(status().isNotFound());
    }

    // ── DONE transition blocked by dependencies ───────────────────────────────

    @Test
    void doneTransition_unresolvedBlocker_returns422_withBlockerMessage() throws Exception {
        addDep(ticketAId, ticketBId); // ticketB is TODO — unresolved
        advanceToInReview(ticketAId);

        UpdateTicketRequest req = new UpdateTicketRequest();
        req.setStatus(TicketStatus.DONE);

        mockMvc.perform(put("/tickets/" + ticketAId)
                        .header("Authorization", bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.message").value(containsString("unresolved blockers")));
    }

    @Test
    void doneTransition_allBlockersResolved_returns200() throws Exception {
        addDep(ticketAId, ticketBId);
        advanceToInReview(ticketAId);
        advanceToDone(ticketBId); // resolve the blocker

        UpdateTicketRequest req = new UpdateTicketRequest();
        req.setStatus(TicketStatus.DONE);

        mockMvc.perform(put("/tickets/" + ticketAId)
                        .header("Authorization", bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DONE"));
    }

    @Test
    void doneTransition_softDeletedBlockerWithTodoStatus_stillBlocks() throws Exception {
        // ticketB is soft-deleted but status remains TODO → still counts as unresolved
        addDep(ticketAId, ticketBId);
        advanceToInReview(ticketAId);

        mockMvc.perform(delete("/tickets/" + ticketBId)
                        .header("Authorization", bearer()))
                .andExpect(status().isOk());

        UpdateTicketRequest req = new UpdateTicketRequest();
        req.setStatus(TicketStatus.DONE);

        mockMvc.perform(put("/tickets/" + ticketAId)
                        .header("Authorization", bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.message").value(containsString("unresolved blockers")));
    }

    @Test
    void doneTransition_softDeletedBlockerWithDoneStatus_doesNotBlock() throws Exception {
        // ticketB is advanced to DONE then soft-deleted — DONE status means resolved
        addDep(ticketAId, ticketBId);
        advanceToInReview(ticketAId);
        advanceToDone(ticketBId);

        mockMvc.perform(delete("/tickets/" + ticketBId)
                        .header("Authorization", bearer()))
                .andExpect(status().isOk());

        UpdateTicketRequest req = new UpdateTicketRequest();
        req.setStatus(TicketStatus.DONE);

        mockMvc.perform(put("/tickets/" + ticketAId)
                        .header("Authorization", bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DONE"));
    }

    // ── audit log ─────────────────────────────────────────────────────────────

    @Test
    void addDependency_writesCreateAuditLog() throws Exception {
        addDep(ticketAId, ticketBId);

        String body = mockMvc.perform(get("/audit-logs")
                        .param("action", "CREATE")
                        .param("entityType", "DEPENDENCY")
                        .param("entityId", String.valueOf(ticketAId))
                        .header("Authorization", bearer()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        JsonNode data = objectMapper.readTree(body).get("data");
        assertThat(data).hasSize(1);
        JsonNode log = data.get(0);
        assertThat(log.get("action").asText()).isEqualTo("CREATE");
        assertThat(log.get("entityType").asText()).isEqualTo("DEPENDENCY");
        assertThat(log.get("entityId").asLong()).isEqualTo(ticketAId);
        assertThat(log.get("actor").asText()).isEqualTo("USER");
        assertThat(log.get("performedBy").isNull()).isFalse();
        assertThat(log.get("performedBy").asLong()).isEqualTo(adminId);
    }

    @Test
    void removeDependency_writesDeleteAuditLog() throws Exception {
        addDep(ticketAId, ticketBId);

        mockMvc.perform(delete("/tickets/" + ticketAId + "/dependencies/" + ticketBId)
                        .header("Authorization", bearer()))
                .andExpect(status().isOk());

        String body = mockMvc.perform(get("/audit-logs")
                        .param("action", "DELETE")
                        .param("entityType", "DEPENDENCY")
                        .param("entityId", String.valueOf(ticketAId))
                        .header("Authorization", bearer()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        JsonNode data = objectMapper.readTree(body).get("data");
        assertThat(data).hasSize(1);
        JsonNode log = data.get(0);
        assertThat(log.get("action").asText()).isEqualTo("DELETE");
        assertThat(log.get("entityType").asText()).isEqualTo("DEPENDENCY");
        assertThat(log.get("entityId").asLong()).isEqualTo(ticketAId);
        assertThat(log.get("actor").asText()).isEqualTo("USER");
        assertThat(log.get("performedBy").isNull()).isFalse();
        assertThat(log.get("performedBy").asLong()).isEqualTo(adminId);
    }

    // ── auth ──────────────────────────────────────────────────────────────────

    @Test
    void addDependency_noAuth_returns401() throws Exception {
        AddDependencyRequest req = new AddDependencyRequest();
        req.setBlockedBy(ticketBId);

        mockMvc.perform(post("/tickets/" + ticketAId + "/dependencies")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void getDependencies_noAuth_returns401() throws Exception {
        mockMvc.perform(get("/tickets/" + ticketAId + "/dependencies"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void removeDependency_noAuth_returns401() throws Exception {
        mockMvc.perform(delete("/tickets/" + ticketAId + "/dependencies/" + ticketBId))
                .andExpect(status().isUnauthorized());
    }
}
