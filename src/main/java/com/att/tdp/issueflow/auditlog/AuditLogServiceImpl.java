package com.att.tdp.issueflow.auditlog;

import com.att.tdp.issueflow.auditlog.dto.AuditLogPageResponse;
import com.att.tdp.issueflow.auditlog.dto.AuditLogResponse;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AuditLogServiceImpl implements AuditLogService {

    private final AuditLogRepository auditLogRepository;

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(AuditAction action, AuditEntityType entityType, Long entityId,
                       Long performedBy, String details) {
        AuditActor actor = performedBy == null ? AuditActor.SYSTEM : AuditActor.USER;
        auditLogRepository.save(AuditLog.builder()
                .action(action)
                .entityType(entityType)
                .entityId(entityId)
                .actor(actor)
                .performedBy(performedBy)
                .details(details)
                .timestamp(LocalDateTime.now())
                .build());
    }

    @Override
    public AuditLogPageResponse getAuditLogs(AuditAction action, AuditEntityType entityType, Long entityId,
                                              AuditActor actor, Long performedBy, int page, int pageSize) {
        Pageable pageable = PageRequest.of(page, pageSize, Sort.by(Sort.Direction.DESC, "timestamp"));
        Page<AuditLog> result = auditLogRepository.findAll(buildSpec(action, entityType, entityId, actor, performedBy), pageable);
        return AuditLogPageResponse.builder()
                .data(result.getContent().stream().map(AuditLogResponse::from).toList())
                .total(result.getTotalElements())
                .page(page)
                .pageSize(pageSize)
                .build();
    }

    private Specification<AuditLog> buildSpec(AuditAction action, AuditEntityType entityType,
                                               Long entityId, AuditActor actor, Long performedBy) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (action != null)      predicates.add(cb.equal(root.get("action"), action));
            if (entityType != null)  predicates.add(cb.equal(root.get("entityType"), entityType));
            if (entityId != null)    predicates.add(cb.equal(root.get("entityId"), entityId));
            if (actor != null)       predicates.add(cb.equal(root.get("actor"), actor));
            if (performedBy != null) predicates.add(cb.equal(root.get("performedBy"), performedBy));
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}
