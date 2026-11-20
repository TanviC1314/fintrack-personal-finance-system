package com.fintrack.transfer.controller;

import com.fintrack.common.dto.PageResponse;
import com.fintrack.security.AuthenticatedUser;
import com.fintrack.transfer.dto.CreateTransferRequest;
import com.fintrack.transfer.dto.TransferResponse;
import com.fintrack.transfer.service.TransferResult;
import com.fintrack.transfer.service.TransferService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Transfers", description = "Moving money between your own accounts. Creating a transfer is idempotent.")
@RestController
@RequestMapping("/api/v1/transfers")
public class TransferController {

    static final String REPLAY_HEADER = "Idempotent-Replayed";

    private final TransferService transferService;

    public TransferController(TransferService transferService) {
        this.transferService = transferService;
    }

    /**
     * Creates a transfer. The required Idempotency-Key header makes retries safe: repeating the same
     * request returns the original transfer with 200 and an Idempotent-Replayed header instead of
     * moving the money again.
     */
    @Operation(summary = "Transfer money between two of your accounts (idempotent)")
    @PostMapping
    public ResponseEntity<TransferResponse> create(
            @AuthenticationPrincipal AuthenticatedUser caller,
            @Parameter(description = "A unique value (1 to 100 printable characters, no spaces) for this transfer. "
                    + "Repeating the request with the same key and body returns the original transfer "
                    + "instead of moving the money again.")
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody CreateTransferRequest request) {
        TransferResult result = transferService.transfer(caller.id(), request, idempotencyKey);
        TransferResponse body = TransferResponse.from(result.transfer());
        if (result.replayed()) {
            return ResponseEntity.ok().header(REPLAY_HEADER, "true").body(body);
        }
        return ResponseEntity.status(HttpStatus.CREATED).body(body);
    }

    /** Lists the caller's transfers, newest first. Optional accountId matches either side of the transfer. */
    @Operation(summary = "List your transfers, newest first")
    @GetMapping
    public PageResponse<TransferResponse> list(
            @AuthenticationPrincipal AuthenticatedUser caller,
            @RequestParam(required = false) UUID accountId,
            Pageable pageable) {
        return PageResponse.from(transferService.list(caller.id(), accountId, pageable).map(TransferResponse::from));
    }
}
