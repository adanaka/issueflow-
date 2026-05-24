package com.att.tdp.issueflow.importexport;

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
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class TicketImportExportIntegrationTest extends BaseIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired UserRepository userRepository;
    @Autowired ProjectRepository projectRepository;
    @Autowired TicketRepository ticketRepository;

    private Long projectId;
    private Long ticketId;
    private Long userId;

    @BeforeEach
    void setUp() {
        User owner = userRepository.save(User.builder()
                .username("owner").email("owner@test.com").fullName("Owner").role(UserRole.ADMIN).build());
        userId = owner.getId();

        Project project = projectRepository.save(Project.builder()
                .name("Test Project").description("desc").owner(owner).build());
        projectId = project.getId();

        Ticket ticket = ticketRepository.save(Ticket.builder()
                .title("Existing Ticket").priority(TicketPriority.HIGH).type(TicketType.BUG)
                .status(TicketStatus.TODO).project(project).build());
        ticketId = ticket.getId();
    }

    // ── Export ────────────────────────────────────────────────────────────────

    @Test
    @WithMockUser(roles = "DEVELOPER")
    void export_returnsHeaderAndDataRow() throws Exception {
        String csv = mockMvc.perform(get("/tickets/export?projectId=" + projectId))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", org.hamcrest.Matchers.containsString("text/csv")))
                .andExpect(header().string("Content-Disposition",
                        "attachment; filename=\"tickets-" + projectId + ".csv\""))
                .andReturn().getResponse().getContentAsString();

        String[] lines = csv.split("\r?\n");
        assertThat(lines[0]).isEqualTo("id,title,description,status,priority,type,assigneeId");
        assertThat(lines[1]).contains("Existing Ticket").contains("HIGH").contains("BUG").contains("TODO");
    }

    @Test
    @WithMockUser(roles = "DEVELOPER")
    void export_projectWithNoActiveTickets_returnsHeaderOnly() throws Exception {
        Ticket t = ticketRepository.findById(ticketId).orElseThrow();
        t.setDeletedAt(java.time.LocalDateTime.now());
        ticketRepository.save(t);

        String csv = mockMvc.perform(get("/tickets/export?projectId=" + projectId))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(csv.trim()).isEqualTo("id,title,description,status,priority,type,assigneeId");
    }

    @Test
    @WithMockUser(roles = "DEVELOPER")
    void export_unknownProject_returns404() throws Exception {
        mockMvc.perform(get("/tickets/export?projectId=99999"))
                .andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser(roles = "DEVELOPER")
    void export_nullFieldsWrittenAsEmpty() throws Exception {
        String csv = mockMvc.perform(get("/tickets/export?projectId=" + projectId))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(csv).doesNotContain("null");
    }

    // ── Import ────────────────────────────────────────────────────────────────

    @Test
    @WithMockUser(roles = "DEVELOPER")
    void import_validRows_createsTickets() throws Exception {
        String csvContent = "id,title,description,status,priority,type,assigneeId\n"
                + "999,Imported Bug,desc,TODO,HIGH,BUG,\n"
                + "888,Imported Feature,,IN_PROGRESS,LOW,FEATURE,\n";

        MockMultipartFile file = new MockMultipartFile("file", "tickets.csv",
                "text/csv", csvContent.getBytes(StandardCharsets.UTF_8));

        mockMvc.perform(multipart("/tickets/import")
                        .file(file)
                        .param("projectId", projectId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.created").value(2))
                .andExpect(jsonPath("$.failed").value(0))
                .andExpect(jsonPath("$.errors").isEmpty());

        assertThat(ticketRepository.findAllByProjectIdAndDeletedAtIsNull(projectId)).hasSize(3);
    }

    @Test
    @WithMockUser(roles = "DEVELOPER")
    void import_idColumnIgnored_autoGeneratesNewId() throws Exception {
        String csvContent = "id,title,description,status,priority,type,assigneeId\n"
                + ticketId + ",Should Have New Id,,TODO,MEDIUM,BUG,\n";

        MockMultipartFile file = new MockMultipartFile("file", "tickets.csv",
                "text/csv", csvContent.getBytes(StandardCharsets.UTF_8));

        mockMvc.perform(multipart("/tickets/import")
                        .file(file)
                        .param("projectId", projectId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.created").value(1));

        assertThat(ticketRepository.findAllByProjectIdAndDeletedAtIsNull(projectId)).hasSize(2);
    }

    @Test
    @WithMockUser(roles = "DEVELOPER")
    void import_invalidStatus_failsRow() throws Exception {
        String csvContent = "title,status,priority,type\n"
                + "Bad Ticket,BLAH,HIGH,BUG\n";

        MockMultipartFile file = new MockMultipartFile("file", "tickets.csv",
                "text/csv", csvContent.getBytes(StandardCharsets.UTF_8));

        mockMvc.perform(multipart("/tickets/import")
                        .file(file)
                        .param("projectId", projectId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.created").value(0))
                .andExpect(jsonPath("$.failed").value(1))
                .andExpect(jsonPath("$.errors[0].row").value(1))
                .andExpect(jsonPath("$.errors[0].message").value("Invalid status value: BLAH"));
    }

    @Test
    @WithMockUser(roles = "DEVELOPER")
    void import_missingTitle_failsRow() throws Exception {
        String csvContent = "title,status,priority,type\n"
                + ",TODO,HIGH,BUG\n";

        MockMultipartFile file = new MockMultipartFile("file", "tickets.csv",
                "text/csv", csvContent.getBytes(StandardCharsets.UTF_8));

        mockMvc.perform(multipart("/tickets/import")
                        .file(file)
                        .param("projectId", projectId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.created").value(0))
                .andExpect(jsonPath("$.failed").value(1))
                .andExpect(jsonPath("$.errors[0].row").value(1))
                .andExpect(jsonPath("$.errors[0].message").value("Missing required field: title"));
    }

    @Test
    @WithMockUser(roles = "DEVELOPER")
    void import_invalidAssigneeId_failsRow() throws Exception {
        String csvContent = "title,status,priority,type,assigneeId\n"
                + "Ticket,TODO,HIGH,BUG,99999\n";

        MockMultipartFile file = new MockMultipartFile("file", "tickets.csv",
                "text/csv", csvContent.getBytes(StandardCharsets.UTF_8));

        mockMvc.perform(multipart("/tickets/import")
                        .file(file)
                        .param("projectId", projectId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.created").value(0))
                .andExpect(jsonPath("$.failed").value(1))
                .andExpect(jsonPath("$.errors[0].row").value(1));
    }

    @Test
    @WithMockUser(roles = "DEVELOPER")
    void import_failedRowDoesNotStopOtherRows() throws Exception {
        String csvContent = "title,status,priority,type\n"
                + "Valid Ticket,TODO,HIGH,BUG\n"
                + ",TODO,HIGH,BUG\n"
                + "Another Valid,DONE,LOW,FEATURE\n";

        MockMultipartFile file = new MockMultipartFile("file", "tickets.csv",
                "text/csv", csvContent.getBytes(StandardCharsets.UTF_8));

        mockMvc.perform(multipart("/tickets/import")
                        .file(file)
                        .param("projectId", projectId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.created").value(2))
                .andExpect(jsonPath("$.failed").value(1))
                .andExpect(jsonPath("$.errors[0].row").value(2));
    }

    @Test
    @WithMockUser(roles = "DEVELOPER")
    void import_unknownProject_returns404() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "tickets.csv",
                "text/csv", "title,status,priority,type\n".getBytes(StandardCharsets.UTF_8));

        mockMvc.perform(multipart("/tickets/import")
                        .file(file)
                        .param("projectId", "99999"))
                .andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser(roles = "DEVELOPER")
    void import_emptyFile_returnsZeroCounts() throws Exception {
        String csvContent = "title,status,priority,type\n";

        MockMultipartFile file = new MockMultipartFile("file", "tickets.csv",
                "text/csv", csvContent.getBytes(StandardCharsets.UTF_8));

        mockMvc.perform(multipart("/tickets/import")
                        .file(file)
                        .param("projectId", projectId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.created").value(0))
                .andExpect(jsonPath("$.failed").value(0))
                .andExpect(jsonPath("$.errors").isEmpty());
    }

    @Test
    @WithMockUser(roles = "DEVELOPER")
    void import_validAssigneeId_createsTicketWithAssignee() throws Exception {
        String csvContent = "title,status,priority,type,assigneeId\n"
                + "Assigned Ticket,TODO,HIGH,BUG," + userId + "\n";

        MockMultipartFile file = new MockMultipartFile("file", "tickets.csv",
                "text/csv", csvContent.getBytes(StandardCharsets.UTF_8));

        mockMvc.perform(multipart("/tickets/import")
                        .file(file)
                        .param("projectId", projectId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.created").value(1))
                .andExpect(jsonPath("$.failed").value(0));

        Ticket imported = ticketRepository.findAllByProjectIdAndDeletedAtIsNull(projectId).stream()
                .filter(t -> t.getTitle().equals("Assigned Ticket"))
                .findFirst().orElseThrow();
        assertThat(imported.getAssignee()).isNotNull();
        assertThat(imported.getAssignee().getId()).isEqualTo(userId);
    }

    // ── Round-trip ────────────────────────────────────────────────────────────

    @Test
    @WithMockUser(roles = "DEVELOPER")
    void exportThenImport_roundTrip() throws Exception {
        String csv = mockMvc.perform(get("/tickets/export?projectId=" + projectId))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        MockMultipartFile file = new MockMultipartFile("file", "tickets.csv",
                "text/csv", csv.getBytes(StandardCharsets.UTF_8));

        mockMvc.perform(multipart("/tickets/import")
                        .file(file)
                        .param("projectId", projectId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.created").value(1))
                .andExpect(jsonPath("$.failed").value(0));

        assertThat(ticketRepository.findAllByProjectIdAndDeletedAtIsNull(projectId)).hasSize(2);
    }
}
