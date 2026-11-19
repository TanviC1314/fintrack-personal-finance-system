package com.fintrack.category.dto;

import com.fintrack.category.entity.Category;
import java.time.Instant;
import java.util.UUID;

public record CategoryResponse(UUID id, String name, Instant createdAt) {

    public static CategoryResponse from(Category category) {
        return new CategoryResponse(category.getId(), category.getName(), category.getCreatedAt());
    }
}
