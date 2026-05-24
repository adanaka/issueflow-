package com.att.tdp.issueflow.auditlog.dto;

import com.att.tdp.issueflow.auditlog.AuditLog;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class AuditLogResponse {
    private Long id;
    private String action;
    private String entityType;
    private Long entityId;
    private String actor;
    private Long performedBy;
    private String details;
    private LocalDateTime timestamp;

    public static AuditLogResponse from(AuditLog log) {
        return AuditLogResponse.builder()
                .id(log.getId())
                .action(log.getAction().name())
                .entityType(log.getEntityType().name())
                .entityId(log.getEntityId())
                .actor(log.getActor().name())
                .performedBy(log.getPerformedBy())
                .details(log.getDetails())
                .timestamp(log.getTimestamp())
                .build();
    }
}
