package com.att.tdp.issueflow.attachment.dto;

import com.att.tdp.issueflow.attachment.Attachment;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class AttachmentResponse {

    private Long id;
    private Long ticketId;
    private String filename;
    private String contentType;
    private Long fileSize;

    public static AttachmentResponse from(Attachment attachment) {
        return AttachmentResponse.builder()
                .id(attachment.getId())
                .ticketId(attachment.getTicket().getId())
                .filename(attachment.getFilename())
                .contentType(attachment.getContentType())
                .fileSize(attachment.getFileSize())
                .build();
    }
}
