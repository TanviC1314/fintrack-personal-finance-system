package com.fintrack.dashboard.controller;

import com.fintrack.dashboard.dto.CategorySpendingReport;
import com.fintrack.dashboard.dto.DashboardSummary;
import com.fintrack.dashboard.dto.MonthlyTrend;
import com.fintrack.dashboard.service.DashboardService;
import com.fintrack.security.AuthenticatedUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.YearMonth;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Month is given as yyyy-MM and defaults to the current month. Currency defaults to the currency of the
 * caller's oldest active account; every figure is for that one currency.
 */
@Tag(name = "Dashboard", description = "Aggregated figures, computed by the database for one currency at a time.")
@RestController
@RequestMapping("/api/v1/dashboard")
public class DashboardController {

    private final DashboardService dashboardService;

    public DashboardController(DashboardService dashboardService) {
        this.dashboardService = dashboardService;
    }

    /** Total balance, the month's income, expenses and savings, and the ten newest transactions. */
    @Operation(summary = "Total balance, monthly income, expenses and savings, and recent transactions")
    @GetMapping("/summary")
    public DashboardSummary summary(
            @AuthenticationPrincipal AuthenticatedUser caller,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM") YearMonth month,
            @RequestParam(required = false) String currency) {
        return dashboardService.summary(caller.id(), month, currency);
    }

    /** Income against expenses for the last {@code months} (default 6, at most 24) months up to {@code to}. */
    @Operation(summary = "Income against expenses for consecutive months")
    @GetMapping("/monthly")
    public MonthlyTrend monthly(
            @AuthenticationPrincipal AuthenticatedUser caller,
            @RequestParam(required = false) Integer months,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM") YearMonth to,
            @RequestParam(required = false) String currency) {
        return dashboardService.monthlyTrend(caller.id(), months, to, currency);
    }

    /** The month's spending split by category, largest first. */
    @Operation(summary = "Where a month's spending went, by category")
    @GetMapping("/categories")
    public CategorySpendingReport categories(
            @AuthenticationPrincipal AuthenticatedUser caller,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM") YearMonth month,
            @RequestParam(required = false) String currency) {
        return dashboardService.spendingByCategory(caller.id(), month, currency);
    }
}
