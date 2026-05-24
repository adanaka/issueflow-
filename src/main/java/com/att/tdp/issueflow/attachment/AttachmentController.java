package com.att.tdp.issueflow.attachment;

import com.att.tdp.issueflow.attachment.dto.AttachmentResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/tickets/{ticketId}/attachments")
@RequiredArgsConstructor
public class AttachmentController {

    private final AttachmentService attachmentService;

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<AttachmentResponse> uploadAttachment(
            @PathVariable Long ticketId,
            @RequestParam("file") MultipartFile file) {
        return ResponseEntity.ok(attachmentService.uploadAttachment(ticketId, file));
    }

    @DeleteMapping("/{attachmentId}")
    public ResponseEntity<Void> deleteAttachment(
            @PathVariable Long ticketId,
            @PathVariable Long attachmentId) {
        attachmentService.deleteAttachment(ticketId, attachmentId);
        return ResponseEntity.ok().build();
    }
}
