package com.fintrack.auth.service;

import com.fintrack.auth.dto.LoginRequest;
import com.fintrack.auth.dto.TokenResponse;
import com.fintrack.auth.exception.InvalidCredentialsException;
import com.fintrack.security.JwtService;
import com.fintrack.user.entity.User;
import com.fintrack.user.repository.UserRepository;
import java.util.Locale;
import java.util.Optional;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class AuthenticationService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    // Checked when the email is unknown so both failure paths cost one BCrypt comparison,
    // which keeps response time from revealing whether an email is registered.
    private final String unknownUserHash;

    public AuthenticationService(UserRepository userRepository, PasswordEncoder passwordEncoder, JwtService jwtService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.unknownUserHash = passwordEncoder.encode("unknown-user-placeholder");
    }

    public TokenResponse login(LoginRequest request) {
        String email = request.email().strip().toLowerCase(Locale.ROOT);
        Optional<User> user = userRepository.findByEmail(email);

        String hashToCheck = user.map(User::getPasswordHash).orElse(unknownUserHash);
        boolean passwordMatches = passwordEncoder.matches(request.password(), hashToCheck);

        if (user.isEmpty() || !passwordMatches) {
            throw new InvalidCredentialsException();
        }

        String token = jwtService.generateToken(user.get().getId(), user.get().getEmail());
        return TokenResponse.bearer(token, jwtService.expiresInSeconds());
    }
}
