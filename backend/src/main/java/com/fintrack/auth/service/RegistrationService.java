package com.fintrack.auth.service;

import com.fintrack.auth.dto.RegisterRequest;
import com.fintrack.auth.exception.EmailAlreadyExistsException;
import com.fintrack.user.entity.User;
import com.fintrack.user.repository.UserRepository;
import java.util.Locale;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RegistrationService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public RegistrationService(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public User register(RegisterRequest request) {
        String email = request.email().strip().toLowerCase(Locale.ROOT);

        if (userRepository.existsByEmail(email)) {
            throw new EmailAlreadyExistsException();
        }

        User user = new User(request.name().strip(), email, passwordEncoder.encode(request.password()));
        try {
            return userRepository.saveAndFlush(user);
        } catch (DataIntegrityViolationException e) {
            // Two concurrent registrations can both pass the exists check; the unique
            // constraint is the real guard, so translate its violation to the same error.
            throw new EmailAlreadyExistsException();
        }
    }
}
