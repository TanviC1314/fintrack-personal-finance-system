package com.fintrack.transaction.controller;

import com.fintrack.common.dto.PageResponse;
import com.fintrack.security.AuthenticatedUser;
import com.fintrack.transaction.dto.CreateTransactionRequest;
import com.fintrack.transaction.dto.TransactionFilter;
import com.fintrack.transaction.dto.TransactionResponse;
import com.fintrack.transaction.dto.UpdateTransactionRequest;
import com.fintrack.transaction.entity.Transaction;
import com.fintrack.transaction.entity.TransactionType;
import com.fintrack.transaction.service.TransactionService;
import jakarta.validation.Valid;
import java.net.URI;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/transactions")
public class TransactionController {

    private final TransactionService transactionService;

    public TransactionController(TransactionService transactionService) {
        this.transactionService = transactionService;
    }

    @PostMapping
    public ResponseEntity<TransactionResponse> create(
            @AuthenticationPrincipal AuthenticatedUser caller, @Valid @RequestBody CreateTransactionRequest request) {
        Transaction transaction = transactionService.create(caller.id(), request);
        return ResponseEntity.created(URI.create("/api/v1/transactions/" + transaction.getId()))
                .body(TransactionResponse.from(transaction));
    }

    /**
     * Lists the caller's transactions. Optional filters: accountId, type, category (case-insensitive),
     * from/to (inclusive dates, ISO format) and includeReversed. Paging: page (from 0), size (max 100)
     * and sort, e.g. sort=amount,desc. Default order is newest transaction date first.
     */
    @GetMapping
    public PageResponse<TransactionResponse> list(
            @AuthenticationPrincipal AuthenticatedUser caller,
            @RequestParam(required = false) UUID accountId,
            @RequestParam(required = false) TransactionType type,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "false") boolean includeReversed,
            Pageable pageable) {
        TransactionFilter filter = new TransactionFilter(accountId, type, category, from, to, includeReversed);
        return PageResponse.from(transactionService.search(caller.id(), filter, pageable)
                .map(TransactionResponse::from));
    }

    @GetMapping("/{id}")
    public TransactionResponse get(@AuthenticationPrincipal AuthenticatedUser caller, @PathVariable UUID id) {
        return TransactionResponse.from(transactionService.get(caller.id(), id));
    }

    @PutMapping("/{id}")
    public TransactionResponse update(
            @AuthenticationPrincipal AuthenticatedUser caller,
            @PathVariable UUID id,
            @Valid @RequestBody UpdateTransactionRequest request) {
        return TransactionResponse.from(transactionService.update(caller.id(), id, request));
    }

    /** Reverses the transaction. The row is kept (marked reversed) so the history stays auditable. */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> reverse(@AuthenticationPrincipal AuthenticatedUser caller, @PathVariable UUID id) {
        transactionService.reverse(caller.id(), id);
        return ResponseEntity.noContent().build();
    }
}
