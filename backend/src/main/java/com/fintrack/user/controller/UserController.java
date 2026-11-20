package com.fintrack.user.controller;

import com.fintrack.security.AuthenticatedUser;
import com.fintrack.user.dto.UserResponse;
import com.fintrack.user.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Users", description = "The signed-in user.")
@RestController
@RequestMapping("/api/v1/users")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @Operation(summary = "Get the signed-in user's profile")
    @GetMapping("/me")
    public UserResponse currentUser(@AuthenticationPrincipal AuthenticatedUser caller) {
        return UserResponse.from(userService.getById(caller.id()));
    }
}
