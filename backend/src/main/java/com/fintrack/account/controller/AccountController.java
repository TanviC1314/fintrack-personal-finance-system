package com.fintrack.account.controller;

import com.fintrack.account.dto.AccountResponse;
import com.fintrack.account.dto.CreateAccountRequest;
import com.fintrack.account.dto.UpdateAccountRequest;
import com.fintrack.account.entity.Account;
import com.fintrack.account.service.AccountService;
import com.fintrack.security.AuthenticatedUser;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/accounts")
public class AccountController {

    private final AccountService accountService;

    public AccountController(AccountService accountService) {
        this.accountService = accountService;
    }

    @PostMapping
    public ResponseEntity<AccountResponse> create(
            @AuthenticationPrincipal AuthenticatedUser caller, @Valid @RequestBody CreateAccountRequest request) {
        Account account = accountService.create(caller.id(), request);
        return ResponseEntity.created(URI.create("/api/v1/accounts/" + account.getId()))
                .body(AccountResponse.from(account));
    }

    @GetMapping
    public List<AccountResponse> list(
            @AuthenticationPrincipal AuthenticatedUser caller,
            @RequestParam(defaultValue = "false") boolean includeArchived) {
        return accountService.list(caller.id(), includeArchived).stream().map(AccountResponse::from).toList();
    }

    @GetMapping("/{id}")
    public AccountResponse get(@AuthenticationPrincipal AuthenticatedUser caller, @PathVariable UUID id) {
        return AccountResponse.from(accountService.get(caller.id(), id));
    }

    @PutMapping("/{id}")
    public AccountResponse rename(
            @AuthenticationPrincipal AuthenticatedUser caller,
            @PathVariable UUID id,
            @Valid @RequestBody UpdateAccountRequest request) {
        return AccountResponse.from(accountService.rename(caller.id(), id, request.name()));
    }

    /** Archives the account; accounts are never physically deleted so their history is preserved. */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> archive(@AuthenticationPrincipal AuthenticatedUser caller, @PathVariable UUID id) {
        accountService.archive(caller.id(), id);
        return ResponseEntity.noContent().build();
    }
}
