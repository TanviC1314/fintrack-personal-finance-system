package com.fintrack.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fintrack.auth.dto.RegisterRequest;
import com.fintrack.auth.exception.EmailAlreadyExistsException;
import com.fintrack.user.entity.User;
import com.fintrack.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class RegistrationServiceTest {

    @Mock
    private UserRepository userRepository;

    // Real encoder (low cost factor for speed): we want to assert on real hashing behaviour.
    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder(4);

    private RegistrationService registrationService;

    @BeforeEach
    void setUp() {
        registrationService = new RegistrationService(userRepository, passwordEncoder);
    }

    @Test
    void storesNormalisedEmailAndHashedPassword() {
        when(userRepository.existsByEmail("alice@example.com")).thenReturn(false);
        when(userRepository.saveAndFlush(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        registrationService.register(new RegisterRequest("  Alice  ", "  Alice@Example.COM ", "s3curePass"));

        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
        verify(userRepository).saveAndFlush(saved.capture());
        User user = saved.getValue();
        assertThat(user.getName()).isEqualTo("Alice");
        assertThat(user.getEmail()).isEqualTo("alice@example.com");
        assertThat(user.getPasswordHash()).isNotEqualTo("s3curePass");
        assertThat(passwordEncoder.matches("s3curePass", user.getPasswordHash())).isTrue();
    }

    @Test
    void rejectsEmailThatIsAlreadyRegisteredIgnoringCase() {
        when(userRepository.existsByEmail("alice@example.com")).thenReturn(true);

        assertThatThrownBy(() -> registrationService.register(
                new RegisterRequest("Alice", "ALICE@example.com", "s3curePass")))
                .isInstanceOf(EmailAlreadyExistsException.class);

        verify(userRepository, never()).saveAndFlush(any());
    }

    @Test
    void translatesUniqueConstraintRaceIntoEmailAlreadyExists() {
        when(userRepository.existsByEmail("alice@example.com")).thenReturn(false);
        when(userRepository.saveAndFlush(any(User.class))).thenThrow(new DataIntegrityViolationException("duplicate"));

        assertThatThrownBy(() -> registrationService.register(
                new RegisterRequest("Alice", "alice@example.com", "s3curePass")))
                .isInstanceOf(EmailAlreadyExistsException.class);
    }
}
