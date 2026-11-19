package com.fintrack.auth.dto;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class RegisterRequestValidationTest {

    private static ValidatorFactory factory;
    private static Validator validator;

    @BeforeAll
    static void setUp() {
        factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @AfterAll
    static void tearDown() {
        factory.close();
    }

    @Test
    void validRequestHasNoViolations() {
        assertThat(violatedFields(new RegisterRequest("Alice", "alice@example.com", "s3curePass"))).isEmpty();
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    void blankNameIsRejected(String name) {
        assertThat(violatedFields(new RegisterRequest(name, "alice@example.com", "s3curePass"))).containsExactly("name");
    }

    @Test
    void nameLongerThanColumnIsRejected() {
        String tooLong = "a".repeat(101);

        assertThat(violatedFields(new RegisterRequest(tooLong, "alice@example.com", "s3curePass"))).containsExactly("name");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"not-an-email", "missing-domain@", "@no-local.com", "user@localhost"})
    void invalidEmailIsRejected(String email) {
        assertThat(violatedFields(new RegisterRequest("Alice", email, "s3curePass"))).contains("email");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {
            "short1",              // under 8 characters
            "onlyletterspassword", // no digit
            "1234567890"           // no letter
    })
    void weakPasswordIsRejected(String password) {
        assertThat(violatedFields(new RegisterRequest("Alice", "alice@example.com", password))).contains("password");
    }

    @Test
    void passwordLongerThanBcryptLimitIsRejected() {
        String tooLong = "a1".repeat(37); // 74 characters

        assertThat(violatedFields(new RegisterRequest("Alice", "alice@example.com", tooLong))).containsExactly("password");
    }

    private Set<String> violatedFields(RegisterRequest request) {
        return validator.validate(request).stream()
                .map(ConstraintViolation::getPropertyPath)
                .map(Object::toString)
                .collect(Collectors.toSet());
    }
}
