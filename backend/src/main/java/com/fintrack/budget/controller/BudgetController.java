package com.fintrack.budget.controller;

import com.fintrack.budget.dto.BudgetResponse;
import com.fintrack.budget.dto.CreateBudgetRequest;
import com.fintrack.budget.dto.UpdateBudgetRequest;
import com.fintrack.budget.service.BudgetService;
import com.fintrack.security.AuthenticatedUser;
import jakarta.validation.Valid;
import java.net.URI;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/budgets")
public class BudgetController {

    private final BudgetService budgetService;

    public BudgetController(BudgetService budgetService) {
        this.budgetService = budgetService;
    }

    @PostMapping
    public ResponseEntity<BudgetResponse> create(
            @AuthenticationPrincipal AuthenticatedUser caller, @Valid @RequestBody CreateBudgetRequest request) {
        BudgetResponse created = budgetService.create(caller.id(), request);
        return ResponseEntity.created(URI.create("/api/v1/budgets/" + created.id())).body(created);
    }

    /**
     * Budgets for one month (yyyy-MM, default current) with how much of each has been spent. Use
     * exceededOnly=true to list just the budgets that have been overspent.
     */
    @GetMapping
    public List<BudgetResponse> list(
            @AuthenticationPrincipal AuthenticatedUser caller,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM") YearMonth month,
            @RequestParam(required = false) String currency,
            @RequestParam(defaultValue = "false") boolean exceededOnly) {
        return budgetService.list(caller.id(), month, currency, exceededOnly);
    }

    @PutMapping("/{id}")
    public BudgetResponse update(
            @AuthenticationPrincipal AuthenticatedUser caller,
            @PathVariable UUID id,
            @Valid @RequestBody UpdateBudgetRequest request) {
        return budgetService.updateAmount(caller.id(), id, request.amount());
    }
}
