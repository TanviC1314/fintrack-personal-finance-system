package com.fintrack.user.controller;

import com.fintrack.security.AuthenticatedUser;
import com.fintrack.user.dto.UserResponse;
import com.fintrack.user.service.UserService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/users")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping("/me")
    public UserResponse currentUser(@AuthenticationPrincipal AuthenticatedUser caller) {
        return UserResponse.from(userService.getById(caller.id()));
    }
}
