package com.fintrack.category.controller;

import com.fintrack.category.dto.CategoryRequest;
import com.fintrack.category.dto.CategoryResponse;
import com.fintrack.category.entity.Category;
import com.fintrack.category.service.CategoryService;
import com.fintrack.security.AuthenticatedUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
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
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Categories", description = "Spending categories used by transactions and budgets.")
@RestController
@RequestMapping("/api/v1/categories")
public class CategoryController {

    private final CategoryService categoryService;

    public CategoryController(CategoryService categoryService) {
        this.categoryService = categoryService;
    }

    @Operation(summary = "Create a category")
    @PostMapping
    public ResponseEntity<CategoryResponse> create(
            @AuthenticationPrincipal AuthenticatedUser caller, @Valid @RequestBody CategoryRequest request) {
        Category category = categoryService.create(caller.id(), request.name());
        return ResponseEntity.created(URI.create("/api/v1/categories/" + category.getId()))
                .body(CategoryResponse.from(category));
    }

    @Operation(summary = "List your categories")
    @GetMapping
    public List<CategoryResponse> list(@AuthenticationPrincipal AuthenticatedUser caller) {
        return categoryService.list(caller.id()).stream().map(CategoryResponse::from).toList();
    }

    @Operation(summary = "Rename a category")
    @PutMapping("/{id}")
    public CategoryResponse rename(
            @AuthenticationPrincipal AuthenticatedUser caller,
            @PathVariable UUID id,
            @Valid @RequestBody CategoryRequest request) {
        return CategoryResponse.from(categoryService.rename(caller.id(), id, request.name()));
    }

    @Operation(summary = "Delete a category that nothing uses")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@AuthenticationPrincipal AuthenticatedUser caller, @PathVariable UUID id) {
        categoryService.delete(caller.id(), id);
        return ResponseEntity.noContent().build();
    }
}
