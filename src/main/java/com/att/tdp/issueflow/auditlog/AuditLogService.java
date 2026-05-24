package com.att.tdp.issueflow.auditlog;

import com.att.tdp.issueflow.auditlog.dto.AuditLogPageResponse;

public interface AuditLogService {
    void record(AuditAction action, AuditEntityType entityType, Long entityId, Long performedBy, String details);
    AuditLogPageResponse getAuditLogs(AuditAction action, AuditEntityType entityType, Long entityId,
                                       AuditActor actor, Long performedBy, int page, int pageSize);
}
