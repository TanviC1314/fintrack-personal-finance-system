package com.fintrack.user.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fintrack.support.PostgresTestContainerConfig;
import com.fintrack.user.entity.User;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(PostgresTestContainerConfig.class)
class UserRepositoryIntegrationTest {

    @Autowired
    private UserRepository userRepository;

    @Test
    void savedUserCanBeFoundByEmailWithGeneratedIdAndTimestamps() {
        userRepository.saveAndFlush(new User("Alice", "alice@example.com", "hash"));

        User found = userRepository.findByEmail("alice@example.com").orElseThrow();

        assertThat(found.getId()).isNotNull();
        assertThat(found.getName()).isEqualTo("Alice");
        assertThat(found.getCreatedAt()).isNotNull();
        assertThat(found.getUpdatedAt()).isNotNull();
    }

    @Test
    void existsByEmailReflectsStoredUsers() {
        userRepository.saveAndFlush(new User("Bob", "bob@example.com", "hash"));

        assertThat(userRepository.existsByEmail("bob@example.com")).isTrue();
        assertThat(userRepository.existsByEmail("nobody@example.com")).isFalse();
    }

    @Test
    void databaseRejectsDuplicateEmail() {
        userRepository.saveAndFlush(new User("Carol", "carol@example.com", "hash"));

        assertThatThrownBy(() -> userRepository.saveAndFlush(new User("Carol Two", "carol@example.com", "hash")))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void databaseRejectsEmailThatIsNotLowerCase() {
        assertThatThrownBy(() -> userRepository.saveAndFlush(new User("Dave", "Dave@Example.com", "hash")))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
