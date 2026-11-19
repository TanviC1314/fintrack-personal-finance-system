package com.fintrack.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.fintrack.auth.dto.LoginRequest;
import com.fintrack.auth.dto.TokenResponse;
import com.fintrack.auth.exception.InvalidCredentialsException;
import com.fintrack.security.AuthenticatedUser;
import com.fintrack.security.JwtProperties;
import com.fintrack.security.JwtService;
import com.fintrack.user.entity.User;
import com.fintrack.user.repository.UserRepository;
import java.time.Clock;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class AuthenticationServiceTest {

    @Mock
    private UserRepository userRepository;

    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder(4);
    private final JwtService jwtService = new JwtService(
            new JwtProperties("unit-test-secret-that-is-comfortably-over-32-bytes", Duration.ofMinutes(30)),
            Clock.systemUTC());

    private AuthenticationService authenticationService;
    private User alice;

    @BeforeEach
    void setUp() {
        authenticationService = new AuthenticationService(userRepository, passwordEncoder, jwtService);
        alice = new User("Alice", "alice@example.com", passwordEncoder.encode("s3curePass"));
        // The id is normally generated on persist; a unit test has no database.
        ReflectionTestUtils.setField(alice, "id", UUID.randomUUID());
    }

    @Test
    void validCredentialsReturnATokenThatIdentifiesTheUser() {
        when(userRepository.findByEmail("alice@example.com")).thenReturn(Optional.of(alice));

        TokenResponse response = authenticationService.login(new LoginRequest("alice@example.com", "s3curePass"));

        assertThat(response.tokenType()).isEqualTo("Bearer");
        assertThat(response.expiresIn()).isEqualTo(30 * 60);
        AuthenticatedUser caller = jwtService.parse(response.accessToken());
        assertThat(caller.email()).isEqualTo("alice@example.com");
    }

    @Test
    void emailIsMatchedIgnoringCaseAndSurroundingWhitespace() {
        when(userRepository.findByEmail("alice@example.com")).thenReturn(Optional.of(alice));

        TokenResponse response = authenticationService.login(new LoginRequest("  ALICE@Example.com ", "s3curePass"));

        assertThat(response.accessToken()).isNotBlank();
    }

    @Test
    void wrongPasswordIsRejected() {
        when(userRepository.findByEmail("alice@example.com")).thenReturn(Optional.of(alice));

        assertThatThrownBy(() -> authenticationService.login(new LoginRequest("alice@example.com", "wrong-password1")))
                .isInstanceOf(InvalidCredentialsException.class);
    }

    @Test
    void unknownEmailIsRejectedWithTheSameErrorAsAWrongPassword() {
        when(userRepository.findByEmail("nobody@example.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authenticationService.login(new LoginRequest("nobody@example.com", "s3curePass")))
                .isInstanceOf(InvalidCredentialsException.class)
                .hasMessage("Invalid email or password");
    }

    @Test
    void passwordIsNeverAcceptedForAnUnknownUserEvenIfItMatchesThePlaceholder() {
        when(userRepository.findByEmail("nobody@example.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authenticationService.login(
                new LoginRequest("nobody@example.com", "unknown-user-placeholder")))
                .isInstanceOf(InvalidCredentialsException.class);
    }
}
