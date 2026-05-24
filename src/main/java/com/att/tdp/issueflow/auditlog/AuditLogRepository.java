package com.att.tdp.issueflow.auditlog;

import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.repository.Repository;

public interface AuditLogRepository extends Repository<AuditLog, Long>, JpaSpecificationExecutor<AuditLog> {
    AuditLog save(AuditLog auditLog);
}
