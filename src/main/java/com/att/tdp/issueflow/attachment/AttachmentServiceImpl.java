package com.att.tdp.issueflow.attachment;

import com.att.tdp.issueflow.attachment.dto.AttachmentResponse;
import com.att.tdp.issueflow.auditlog.AuditAction;
import com.att.tdp.issueflow.auditlog.AuditEntityType;
import com.att.tdp.issueflow.auditlog.AuditLogService;
import com.att.tdp.issueflow.common.SecurityUtils;
import com.att.tdp.issueflow.common.exception.BadRequestException;
import com.att.tdp.issueflow.common.exception.ResourceNotFoundException;
import com.att.tdp.issueflow.ticket.Ticket;
import com.att.tdp.issueflow.ticket.TicketRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional
public class AttachmentServiceImpl implements AttachmentService {

    private static final long MAX_FILE_SIZE = 10L * 1024 * 1024;
    private static final Set<String> ALLOWED_TYPES = Set.of(
            "image/png", "image/jpeg", "application/pdf", "text/plain"
    );

    private final TicketRepository ticketRepository;
    private final AttachmentRepository attachmentRepository;
    private final AuditLogService auditLogService;
    private final SecurityUtils securityUtils;

    @Override
    public AttachmentResponse uploadAttachment(Long ticketId, MultipartFile file) {
        Ticket ticket = ticketRepository.findByIdAndDeletedAtIsNull(ticketId)
                .orElseThrow(() -> new ResourceNotFoundException("Ticket not found with id: " + ticketId));

        validateFile(file);

        String originalFilename = StringUtils.cleanPath(
                file.getOriginalFilename() != null ? file.getOriginalFilename() : "file");
        String extension = extractExtension(originalFilename);
        String internalName = UUID.randomUUID() + (extension.isEmpty() ? "" : "." + extension);

        Path dir = Paths.get("uploads", String.valueOf(ticketId));
        Path filePath = dir.resolve(internalName);

        try {
            Files.createDirectories(dir);
            Files.copy(file.getInputStream(), filePath, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new RuntimeException("Failed to store file: " + originalFilename, e);
        }

        Attachment attachment = Attachment.builder()
                .ticket(ticket)
                .filename(originalFilename)
                .contentType(file.getContentType())
                .fileSize(file.getSize())
                .storagePath(filePath.toString())
                .build();

        Attachment saved;
        try {
            saved = attachmentRepository.saveAndFlush(attachment);
        } catch (Exception e) {
            try { Files.deleteIfExists(filePath); } catch (IOException ignored) {}
            throw e;
        }

        auditLogService.record(AuditAction.CREATE, AuditEntityType.ATTACHMENT, saved.getId(),
                securityUtils.getCurrentUserId(),
                "File '" + originalFilename + "' uploaded to ticket " + ticketId);

        return AttachmentResponse.from(saved);
    }

    @Override
    public void deleteAttachment(Long ticketId, Long attachmentId) {
        Attachment attachment = attachmentRepository.findByIdAndTicketId(attachmentId, ticketId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Attachment not found with id: " + attachmentId + " for ticket: " + ticketId));

        String filename = attachment.getFilename();
        String storagePath = attachment.getStoragePath();

        attachmentRepository.delete(attachment);

        try {
            Files.deleteIfExists(Paths.get(storagePath));
        } catch (IOException ignored) {
            // Missing or inaccessible file on disk is acceptable
        }

        auditLogService.record(AuditAction.DELETE, AuditEntityType.ATTACHMENT, attachmentId,
                securityUtils.getCurrentUserId(),
                "File '" + filename + "' deleted from ticket " + ticketId);
    }

    private void validateFile(MultipartFile file) {
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new BadRequestException("File size exceeds the maximum allowed size of 10 MB");
        }
        String type = file.getContentType();
        if (type == null || !ALLOWED_TYPES.contains(type)) {
            throw new BadRequestException(
                    "File type not allowed: " + type + ". Allowed: image/png, image/jpeg, application/pdf, text/plain");
        }
    }

    private String extractExtension(String filename) {
        int dot = filename.lastIndexOf('.');
        return dot >= 0 ? filename.substring(dot + 1) : "";
    }
}
