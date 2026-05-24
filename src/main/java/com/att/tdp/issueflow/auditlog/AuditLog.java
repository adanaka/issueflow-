package com.att.tdp.issueflow.auditlog;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "audit_logs")
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AuditAction action;

    @Enumerated(EnumType.STRING)
    @Column(name = "entity_type", nullable = false, length = 20)
    private AuditEntityType entityType;

    @Column(name = "entity_id", nullable = false)
    private Long entityId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private AuditActor actor;

    @Column(name = "performed_by")
    private Long performedBy;

    @Column(columnDefinition = "TEXT")
    private String details;

    @Column(nullable = false, updatable = false)
    private LocalDateTime timestamp;
}
