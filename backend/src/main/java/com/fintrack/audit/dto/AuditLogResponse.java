package com.fintrack.audit.dto;

import com.fintrack.audit.entity.AuditAction;
import com.fintrack.audit.entity.AuditEntityType;
import com.fintrack.audit.entity.AuditLog;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record AuditLogResponse(
        UUID id,
        AuditEntityType entityType,
        UUID entityId,
        AuditAction action,
        Map<String, Object> details,
        Instant createdAt) {

    public static AuditLogResponse from(AuditLog log) {
        return new AuditLogResponse(
                log.getId(), log.getEntityType(), log.getEntityId(), log.getAction(), log.getDetails(),
                log.getCreatedAt());
    }
}
