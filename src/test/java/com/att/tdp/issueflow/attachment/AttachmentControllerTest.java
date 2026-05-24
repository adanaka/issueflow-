package com.att.tdp.issueflow.attachment;

import com.att.tdp.issueflow.BaseIntegrationTest;
import com.att.tdp.issueflow.auth.dto.LoginRequest;
import com.att.tdp.issueflow.project.Project;
import com.att.tdp.issueflow.project.ProjectRepository;
import com.att.tdp.issueflow.ticket.Ticket;
import com.att.tdp.issueflow.ticket.TicketPriority;
import com.att.tdp.issueflow.ticket.TicketRepository;
import com.att.tdp.issueflow.ticket.TicketStatus;
import com.att.tdp.issueflow.ticket.TicketType;
import com.att.tdp.issueflow.user.User;
import com.att.tdp.issueflow.user.UserRepository;
import com.att.tdp.issueflow.user.UserRole;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class AttachmentControllerTest extends BaseIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired UserRepository userRepository;
    @Autowired ProjectRepository projectRepository;
    @Autowired TicketRepository ticketRepository;
    @Autowired PasswordEncoder passwordEncoder;

    private Long adminId;
    private String adminToken;
    private Long ticketId;
    private Long otherTicketId;

    @BeforeEach
    void setUp() throws Exception {
        User admin = userRepository.save(User.builder()
                .username("admin").email("admin@test.com").fullName("Admin")
                .role(UserRole.ADMIN).password(passwordEncoder.encode("pass")).build());
        adminId = admin.getId();

        Project project = projectRepository.save(Project.builder()
                .name("Project").description("d").owner(admin).build());

        Ticket ticket = ticketRepository.save(Ticket.builder()
                .title("Ticket").priority(TicketPriority.MEDIUM)
                .status(TicketStatus.TODO).type(TicketType.BUG)
                .project(project).build());
        ticketId = ticket.getId();

        Ticket otherTicket = ticketRepository.save(Ticket.builder()
                .title("Other Ticket").priority(TicketPriority.LOW)
                .status(TicketStatus.TODO).type(TicketType.BUG)
                .project(project).build());
        otherTicketId = otherTicket.getId();

        adminToken = login("admin", "pass");
    }

    @AfterEach
    void cleanUploads() throws IOException {
        Path uploadsDir = Paths.get("uploads");
        if (Files.exists(uploadsDir)) {
            Files.walkFileTree(uploadsDir, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    Files.delete(file);
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                    Files.delete(dir);
                    return FileVisitResult.CONTINUE;
                }
            });
        }
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

    private String doUpload(MockMultipartFile file) throws Exception {
        return mockMvc.perform(multipart("/tickets/" + ticketId + "/attachments")
                        .file(file)
                        .header("Authorization", bearer()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
    }

    private Long uploadAndGetId(String filename, String contentType) throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", filename, contentType, ("content:" + filename).getBytes());
        return objectMapper.readTree(doUpload(file)).get("id").asLong();
    }

    private String storagePath(Long attachmentId) {
        return jdbcTemplate.queryForObject(
                "SELECT storage_path FROM attachments WHERE id = ?",
                String.class, attachmentId);
    }

    // ── upload: all four allowed content types ────────────────────────────────

    @Test
    void upload_png_returns200_withCorrectResponseShape() throws Exception {
        byte[] content = "fake png bytes".getBytes();
        MockMultipartFile file = new MockMultipartFile(
                "file", "photo.png", "image/png", content);

        mockMvc.perform(multipart("/tickets/" + ticketId + "/attachments")
                        .file(file)
                        .header("Authorization", bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.ticketId").value(ticketId))
                .andExpect(jsonPath("$.filename").value("photo.png"))
                .andExpect(jsonPath("$.contentType").value("image/png"))
                .andExpect(jsonPath("$.fileSize").value(content.length))
                .andExpect(jsonPath("$.storagePath").doesNotExist());
    }

    @Test
    void upload_jpeg_returns200() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "image.jpg", "image/jpeg", "jpeg bytes".getBytes());

        mockMvc.perform(multipart("/tickets/" + ticketId + "/attachments")
                        .file(file)
                        .header("Authorization", bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.contentType").value("image/jpeg"))
                .andExpect(jsonPath("$.storagePath").doesNotExist());
    }

    @Test
    void upload_pdf_returns200() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "doc.pdf", "application/pdf", "pdf content".getBytes());

        mockMvc.perform(multipart("/tickets/" + ticketId + "/attachments")
                        .file(file)
                        .header("Authorization", bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.contentType").value("application/pdf"))
                .andExpect(jsonPath("$.storagePath").doesNotExist());
    }

    @Test
    void upload_textPlain_returns200() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "notes.txt", "text/plain", "plain text content".getBytes());

        mockMvc.perform(multipart("/tickets/" + ticketId + "/attachments")
                        .file(file)
                        .header("Authorization", bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.contentType").value("text/plain"))
                .andExpect(jsonPath("$.storagePath").doesNotExist());
    }

    // ── upload: validation rejections ─────────────────────────────────────────

    @Test
    void upload_disallowedContentType_returns400_withMessage() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "binary.bin", "application/octet-stream", "data".getBytes());

        mockMvc.perform(multipart("/tickets/" + ticketId + "/attachments")
                        .file(file)
                        .header("Authorization", bearer()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString("File type not allowed")));
    }

    @Test
    void upload_htmlContentType_returns400_withMessage() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "page.html", "text/html", "<html/>".getBytes());

        mockMvc.perform(multipart("/tickets/" + ticketId + "/attachments")
                        .file(file)
                        .header("Authorization", bearer()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString("File type not allowed")));
    }

    @Test
    void upload_exceedsMaxSize_returns400_withMessage() throws Exception {
        byte[] oversized = new byte[10 * 1024 * 1024 + 1];
        MockMultipartFile file = new MockMultipartFile(
                "file", "huge.png", "image/png", oversized);

        mockMvc.perform(multipart("/tickets/" + ticketId + "/attachments")
                        .file(file)
                        .header("Authorization", bearer()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString("10 MB")));
    }

    @Test
    void upload_nonExistentTicket_returns404() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "test.png", "image/png", "data".getBytes());

        mockMvc.perform(multipart("/tickets/9999/attachments")
                        .file(file)
                        .header("Authorization", bearer()))
                .andExpect(status().isNotFound());
    }

    @Test
    void upload_noAuth_returns401() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "test.png", "image/png", "data".getBytes());

        mockMvc.perform(multipart("/tickets/" + ticketId + "/attachments").file(file))
                .andExpect(status().isUnauthorized());
    }

    // ── file stored on disk ───────────────────────────────────────────────────

    @Test
    void upload_fileStoredOnDisk_withUuidBasedName_notOriginalFilename() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "original-name.png", "image/png", "png bytes".getBytes());

        String body = doUpload(file);
        long attachmentId = objectMapper.readTree(body).get("id").asLong();

        String path = storagePath(attachmentId);
        Path storedPath = Paths.get(path);

        assertThat(Files.exists(storedPath)).isTrue();

        String storedFilename = storedPath.getFileName().toString();
        assertThat(storedFilename).isNotEqualToIgnoringCase("original-name.png");
        assertThat(storedFilename)
                .matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\\.png");
    }

    // ── delete ────────────────────────────────────────────────────────────────

    @Test
    void deleteAttachment_returns200_fileRemovedFromDisk() throws Exception {
        Long attachmentId = uploadAndGetId("photo.png", "image/png");
        String path = storagePath(attachmentId);
        assertThat(Files.exists(Paths.get(path))).isTrue();

        mockMvc.perform(delete("/tickets/" + ticketId + "/attachments/" + attachmentId)
                        .header("Authorization", bearer()))
                .andExpect(status().isOk());

        assertThat(Files.exists(Paths.get(path))).isFalse();
    }

    @Test
    void deleteAttachment_nonExistentAttachmentId_returns404() throws Exception {
        mockMvc.perform(delete("/tickets/" + ticketId + "/attachments/9999")
                        .header("Authorization", bearer()))
                .andExpect(status().isNotFound());
    }

    @Test
    void deleteAttachment_wrongTicket_returns404() throws Exception {
        // Attachment belongs to ticketId; DELETE uses otherTicketId → 404
        Long attachmentId = uploadAndGetId("test.pdf", "application/pdf");

        mockMvc.perform(delete("/tickets/" + otherTicketId + "/attachments/" + attachmentId)
                        .header("Authorization", bearer()))
                .andExpect(status().isNotFound());
    }

    @Test
    void deleteAttachment_missingFileOnDisk_returns200() throws Exception {
        Long attachmentId = uploadAndGetId("notes.txt", "text/plain");
        String path = storagePath(attachmentId);

        Files.deleteIfExists(Paths.get(path));
        assertThat(Files.exists(Paths.get(path))).isFalse();

        // DELETE must still succeed — missing file on disk is acceptable
        mockMvc.perform(delete("/tickets/" + ticketId + "/attachments/" + attachmentId)
                        .header("Authorization", bearer()))
                .andExpect(status().isOk());
    }

    @Test
    void deleteAttachment_noAuth_returns401() throws Exception {
        mockMvc.perform(delete("/tickets/" + ticketId + "/attachments/1"))
                .andExpect(status().isUnauthorized());
    }

    // ── audit log ─────────────────────────────────────────────────────────────

    @Test
    void upload_writesCreateAuditLog() throws Exception {
        Long attachmentId = uploadAndGetId("audit.png", "image/png");

        String body = mockMvc.perform(get("/audit-logs")
                        .param("action", "CREATE")
                        .param("entityType", "ATTACHMENT")
                        .param("entityId", String.valueOf(attachmentId))
                        .header("Authorization", bearer()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        JsonNode data = objectMapper.readTree(body).get("data");
        assertThat(data).hasSize(1);
        JsonNode log = data.get(0);
        assertThat(log.get("action").asText()).isEqualTo("CREATE");
        assertThat(log.get("entityType").asText()).isEqualTo("ATTACHMENT");
        assertThat(log.get("entityId").asLong()).isEqualTo(attachmentId);
        assertThat(log.get("actor").asText()).isEqualTo("USER");
        assertThat(log.get("performedBy").isNull()).isFalse();
        assertThat(log.get("performedBy").asLong()).isEqualTo(adminId);
    }

    @Test
    void deleteAttachment_writesDeleteAuditLog() throws Exception {
        Long attachmentId = uploadAndGetId("audit-del.txt", "text/plain");

        mockMvc.perform(delete("/tickets/" + ticketId + "/attachments/" + attachmentId)
                        .header("Authorization", bearer()))
                .andExpect(status().isOk());

        String body = mockMvc.perform(get("/audit-logs")
                        .param("action", "DELETE")
                        .param("entityType", "ATTACHMENT")
                        .param("entityId", String.valueOf(attachmentId))
                        .header("Authorization", bearer()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        JsonNode data = objectMapper.readTree(body).get("data");
        assertThat(data).hasSize(1);
        JsonNode log = data.get(0);
        assertThat(log.get("action").asText()).isEqualTo("DELETE");
        assertThat(log.get("entityType").asText()).isEqualTo("ATTACHMENT");
        assertThat(log.get("entityId").asLong()).isEqualTo(attachmentId);
        assertThat(log.get("actor").asText()).isEqualTo("USER");
        assertThat(log.get("performedBy").isNull()).isFalse();
        assertThat(log.get("performedBy").asLong()).isEqualTo(adminId);
    }
}
