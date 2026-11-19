package com.fintrack.audit.service;

import com.fintrack.audit.entity.AuditAction;
import com.fintrack.audit.entity.AuditEntityType;
import com.fintrack.audit.entity.AuditLog;
import com.fintrack.audit.repository.AuditLogRepository;
import java.util.Map;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuditService {

    private final AuditLogRepository auditLogRepository;

    public AuditService(AuditLogRepository auditLogRepository) {
        this.auditLogRepository = auditLogRepository;
    }

    /**
     * Records an event. It must run inside the transaction of the operation it describes, so the audit
     * entry is committed together with the change and discarded if the change is rolled back.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void record(UUID userId, AuditEntityType entityType, UUID entityId, AuditAction action,
                       Map<String, Object> details) {
        auditLogRepository.save(new AuditLog(userId, entityType, entityId, action, details));
    }

    /** The caller's audit entries, newest first, optionally narrowed to one entity type and/or entity. */
    @Transactional(readOnly = true)
    public Page<AuditLog> list(UUID userId, AuditEntityType entityType, UUID entityId, Pageable pageable) {
        Specification<AuditLog> spec = (root, query, cb) -> cb.equal(root.get("userId"), userId);
        if (entityType != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("entityType"), entityType));
        }
        if (entityId != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("entityId"), entityId));
        }
        Pageable newestFirst = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(),
                Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id")));
        return auditLogRepository.findAll(spec, newestFirst);
    }
}
