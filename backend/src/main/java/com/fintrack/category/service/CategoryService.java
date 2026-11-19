package com.fintrack.category.service;

import com.fintrack.category.entity.Category;
import com.fintrack.category.exception.CategoryNameAlreadyExistsException;
import com.fintrack.category.repository.CategoryRepository;
import com.fintrack.common.exception.ResourceNotFoundException;
import java.util.List;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CategoryService {

    private final CategoryRepository categoryRepository;

    public CategoryService(CategoryRepository categoryRepository) {
        this.categoryRepository = categoryRepository;
    }

    @Transactional
    public Category create(UUID userId, String rawName) {
        String name = rawName.strip();
        ensureNameIsFree(userId, name);
        try {
            return categoryRepository.saveAndFlush(new Category(userId, name));
        } catch (DataIntegrityViolationException e) {
            // A concurrent request created the same name between our check and insert.
            throw new CategoryNameAlreadyExistsException(name);
        }
    }

    @Transactional(readOnly = true)
    public List<Category> list(UUID userId) {
        return categoryRepository.findAllByUserIdOrderByNameAsc(userId);
    }

    /** Returns the caller's category, or reports it as not found if it is missing or someone else's. */
    @Transactional(readOnly = true)
    public Category getOwned(UUID userId, UUID categoryId) {
        return categoryRepository.findByIdAndUserId(categoryId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Category not found"));
    }

    @Transactional
    public Category rename(UUID userId, UUID categoryId, String rawName) {
        Category category = getOwned(userId, categoryId);
        String name = rawName.strip();
        // Changing only the letter case of its own name is not a conflict.
        if (!name.equalsIgnoreCase(category.getName())) {
            ensureNameIsFree(userId, name);
        }
        category.rename(name);
        try {
            return categoryRepository.saveAndFlush(category);
        } catch (DataIntegrityViolationException e) {
            throw new CategoryNameAlreadyExistsException(name);
        }
    }

    @Transactional
    public void delete(UUID userId, UUID categoryId) {
        Category category = getOwned(userId, categoryId);
        categoryRepository.delete(category);
        categoryRepository.flush();
    }

    private void ensureNameIsFree(UUID userId, String name) {
        if (categoryRepository.existsByUserIdAndNameIgnoreCase(userId, name)) {
            throw new CategoryNameAlreadyExistsException(name);
        }
    }
}
