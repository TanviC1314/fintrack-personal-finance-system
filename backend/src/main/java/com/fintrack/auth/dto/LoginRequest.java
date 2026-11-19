package com.fintrack.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record LoginRequest(

        @NotBlank(message = "Email is required")
        @Size(max = 255, message = "Email must be at most 255 characters")
        String email,

        // The 72 cap matches BCrypt's input limit and stops oversized payloads reaching the encoder.
        @NotBlank(message = "Password is required")
        @Size(max = 72, message = "Password must be at most 72 characters")
        String password) {
}
