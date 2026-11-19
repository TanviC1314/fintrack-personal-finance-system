package com.fintrack.audit.controller;

import com.fintrack.audit.dto.AuditLogResponse;
import com.fintrack.audit.entity.AuditEntityType;
import com.fintrack.audit.service.AuditService;
import com.fintrack.common.dto.PageResponse;
import com.fintrack.security.AuthenticatedUser;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/audit-logs")
public class AuditLogController {

    private final AuditService auditService;

    public AuditLogController(AuditService auditService) {
        this.auditService = auditService;
    }

    /** The caller's own audit history, newest first. Read-only: the log is written by the operations themselves. */
    @GetMapping
    public PageResponse<AuditLogResponse> list(
            @AuthenticationPrincipal AuthenticatedUser caller,
            @RequestParam(required = false) AuditEntityType entityType,
            @RequestParam(required = false) UUID entityId,
            Pageable pageable) {
        return PageResponse.from(auditService.list(caller.id(), entityType, entityId, pageable)
                .map(AuditLogResponse::from));
    }
}
