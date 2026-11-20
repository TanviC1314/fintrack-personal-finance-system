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
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
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

@Tag(name = "Transactions", description = "Income and expenses. Deleting reverses a transaction and keeps it in the history.")
@RestController
@RequestMapping("/api/v1/transactions")
public class TransactionController {

    private final TransactionService transactionService;

    public TransactionController(TransactionService transactionService) {
        this.transactionService = transactionService;
    }

    @Operation(summary = "Record an income or an expense")
    @PostMapping
    public ResponseEntity<TransactionResponse> create(
            @AuthenticationPrincipal AuthenticatedUser caller, @Valid @RequestBody CreateTransactionRequest request) {
        Transaction transaction = transactionService.create(caller.id(), request);
        return ResponseEntity.created(URI.create("/api/v1/transactions/" + transaction.getId()))
                .body(TransactionResponse.from(transaction));
    }

    /**
     * Lists the caller's transactions. Optional filters: accountId, type, categoryId,
     * from/to (inclusive dates, ISO format) and includeReversed. Paging: page (from 0), size (max 100)
     * and sort, e.g. sort=amount,desc. Default order is newest transaction date first.
     */
    @Operation(summary = "Search transactions with filters, sorting and paging")
    @GetMapping
    public PageResponse<TransactionResponse> list(
            @AuthenticationPrincipal AuthenticatedUser caller,
            @RequestParam(required = false) UUID accountId,
            @RequestParam(required = false) TransactionType type,
            @RequestParam(required = false) UUID categoryId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "false") boolean includeReversed,
            Pageable pageable) {
        TransactionFilter filter = new TransactionFilter(accountId, type, categoryId, from, to, includeReversed);
        return PageResponse.from(transactionService.search(caller.id(), filter, pageable)
                .map(TransactionResponse::from));
    }

    @Operation(summary = "Get one transaction")
    @GetMapping("/{id}")
    public TransactionResponse get(@AuthenticationPrincipal AuthenticatedUser caller, @PathVariable UUID id) {
        return TransactionResponse.from(transactionService.get(caller.id(), id));
    }

    @Operation(summary = "Change the amount, date, category or description of a transaction")
    @PutMapping("/{id}")
    public TransactionResponse update(
            @AuthenticationPrincipal AuthenticatedUser caller,
            @PathVariable UUID id,
            @Valid @RequestBody UpdateTransactionRequest request) {
        return TransactionResponse.from(transactionService.update(caller.id(), id, request));
    }

    /** Reverses the transaction. The row is kept (marked reversed) so the history stays auditable. */
    @Operation(summary = "Reverse a transaction (the opposite balance change is applied)")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> reverse(@AuthenticationPrincipal AuthenticatedUser caller, @PathVariable UUID id) {
        transactionService.reverse(caller.id(), id);
        return ResponseEntity.noContent().build();
    }
}
