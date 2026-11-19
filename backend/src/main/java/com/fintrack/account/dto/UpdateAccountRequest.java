package com.fintrack.account.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Only the name can be changed. Type, currency and balances are fixed or derived from transactions. */
public record UpdateAccountRequest(

        @NotBlank(message = "Name is required")
        @Size(max = 100, message = "Name must be at most 100 characters")
        String name) {
}
