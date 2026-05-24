package com.att.tdp.issueflow.auditlog;

import com.att.tdp.issueflow.auditlog.dto.AuditLogPageResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/audit-logs")
@RequiredArgsConstructor
public class AuditLogController {

    private final AuditLogService auditLogService;

    @GetMapping
    public ResponseEntity<AuditLogPageResponse> getAuditLogs(
            @RequestParam(required = false) AuditAction action,
            @RequestParam(required = false) AuditEntityType entityType,
            @RequestParam(required = false) Long entityId,
            @RequestParam(required = false) AuditActor actor,
            @RequestParam(required = false) Long performedBy,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int pageSize) {
        return ResponseEntity.ok(
                auditLogService.getAuditLogs(action, entityType, entityId, actor, performedBy, page, pageSize));
    }
}
