package com.fintrack.user.repository;

import com.fintrack.user.entity.User;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, UUID> {

    /** Emails are stored lower-case; callers must normalise before querying. */
    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);
}
