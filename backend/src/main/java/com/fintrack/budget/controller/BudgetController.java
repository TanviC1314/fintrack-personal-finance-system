package com.fintrack.budget.controller;

import com.fintrack.budget.dto.BudgetResponse;
import com.fintrack.budget.dto.CreateBudgetRequest;
import com.fintrack.budget.dto.UpdateBudgetRequest;
import com.fintrack.budget.service.BudgetService;
import com.fintrack.security.AuthenticatedUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
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

@Tag(name = "Budgets", description = "Monthly spending limits per category and currency.")
@RestController
@RequestMapping("/api/v1/budgets")
public class BudgetController {

    private final BudgetService budgetService;

    public BudgetController(BudgetService budgetService) {
        this.budgetService = budgetService;
    }

    @Operation(summary = "Create a budget for a category and month")
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
    @Operation(summary = "List the budgets of a month with how much has been spent")
    @GetMapping
    public List<BudgetResponse> list(
            @AuthenticationPrincipal AuthenticatedUser caller,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM") YearMonth month,
            @RequestParam(required = false) String currency,
            @RequestParam(defaultValue = "false") boolean exceededOnly) {
        return budgetService.list(caller.id(), month, currency, exceededOnly);
    }

    @Operation(summary = "Change a budget's amount")
    @PutMapping("/{id}")
    public BudgetResponse update(
            @AuthenticationPrincipal AuthenticatedUser caller,
            @PathVariable UUID id,
            @Valid @RequestBody UpdateBudgetRequest request) {
        return budgetService.updateAmount(caller.id(), id, request.amount());
    }
}
