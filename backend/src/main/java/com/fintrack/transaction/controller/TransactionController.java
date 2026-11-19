package com.fintrack.transaction.controller;

import com.fintrack.security.AuthenticatedUser;
import com.fintrack.transaction.dto.CreateTransactionRequest;
import com.fintrack.transaction.dto.TransactionResponse;
import com.fintrack.transaction.dto.UpdateTransactionRequest;
import com.fintrack.transaction.entity.Transaction;
import com.fintrack.transaction.service.TransactionService;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
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
