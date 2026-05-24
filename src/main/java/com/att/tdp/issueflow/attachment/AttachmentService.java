package com.att.tdp.issueflow.attachment;

import com.att.tdp.issueflow.attachment.dto.AttachmentResponse;
import org.springframework.web.multipart.MultipartFile;

public interface AttachmentService {
    AttachmentResponse uploadAttachment(Long ticketId, MultipartFile file);
    void deleteAttachment(Long ticketId, Long attachmentId);
}
