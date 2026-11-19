package com.fintrack.category.repository;

import com.fintrack.category.entity.Category;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Every lookup takes the owner's id, so another user's category is indistinguishable from a missing one. */
public interface CategoryRepository extends JpaRepository<Category, UUID> {

    Optional<Category> findByIdAndUserId(UUID id, UUID userId);

    List<Category> findAllByUserIdOrderByNameAsc(UUID userId);

    boolean existsByUserIdAndNameIgnoreCase(UUID userId, String name);
}
