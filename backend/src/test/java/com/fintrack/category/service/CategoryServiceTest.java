package com.fintrack.category.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fintrack.category.entity.Category;
import com.fintrack.category.exception.CategoryInUseException;
import com.fintrack.category.exception.CategoryNameAlreadyExistsException;
import com.fintrack.category.repository.CategoryRepository;
import com.fintrack.common.exception.ResourceNotFoundException;
import com.fintrack.transaction.repository.TransactionRepository;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

@ExtendWith(MockitoExtension.class)
class CategoryServiceTest {

    private final UUID userId = UUID.randomUUID();
    private final UUID categoryId = UUID.randomUUID();

    @Mock
    private CategoryRepository categoryRepository;
    @Mock
    private TransactionRepository transactionRepository;
    @InjectMocks
    private CategoryService service;

    @Test
    void createTrimsTheNameAndOwnsItByTheCaller() {
        when(categoryRepository.existsByUserIdAndNameIgnoreCase(userId, "Food")).thenReturn(false);
        when(categoryRepository.saveAndFlush(any(Category.class))).thenAnswer(inv -> inv.getArgument(0));

        Category created = service.create(userId, "  Food ");

        assertThat(created.getName()).isEqualTo("Food");
        assertThat(created.getUserId()).isEqualTo(userId);
    }

    @Test
    void createRejectsANameTheUserAlreadyHasIgnoringCase() {
        when(categoryRepository.existsByUserIdAndNameIgnoreCase(userId, "FOOD")).thenReturn(true);

        assertThatThrownBy(() -> service.create(userId, "FOOD")).isInstanceOf(CategoryNameAlreadyExistsException.class);
        verify(categoryRepository, never()).saveAndFlush(any());
    }

    @Test
    void createTranslatesAUniqueIndexRaceIntoANameConflict() {
        when(categoryRepository.saveAndFlush(any(Category.class))).thenThrow(new DataIntegrityViolationException("dup"));

        assertThatThrownBy(() -> service.create(userId, "Food")).isInstanceOf(CategoryNameAlreadyExistsException.class);
    }

    @Test
    void getOwnedReportsSomeoneElsesCategoryAsNotFound() {
        when(categoryRepository.findByIdAndUserId(categoryId, userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getOwned(userId, categoryId)).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void renameChangesTheName() {
        Category category = new Category(userId, "Old");
        when(categoryRepository.findByIdAndUserId(categoryId, userId)).thenReturn(Optional.of(category));
        when(categoryRepository.existsByUserIdAndNameIgnoreCase(userId, "New")).thenReturn(false);
        when(categoryRepository.saveAndFlush(category)).thenReturn(category);

        assertThat(service.rename(userId, categoryId, " New ").getName()).isEqualTo("New");
    }

    @Test
    void renameToAnotherCategorysNameIsAConflict() {
        Category category = new Category(userId, "Old");
        when(categoryRepository.findByIdAndUserId(categoryId, userId)).thenReturn(Optional.of(category));
        when(categoryRepository.existsByUserIdAndNameIgnoreCase(userId, "Taken")).thenReturn(true);

        assertThatThrownBy(() -> service.rename(userId, categoryId, "Taken"))
                .isInstanceOf(CategoryNameAlreadyExistsException.class);
        assertThat(category.getName()).isEqualTo("Old");
    }

    @Test
    void renameMayChangeOnlyTheCaseOfItsOwnName() {
        Category category = new Category(userId, "food");
        when(categoryRepository.findByIdAndUserId(categoryId, userId)).thenReturn(Optional.of(category));
        when(categoryRepository.saveAndFlush(category)).thenReturn(category);

        assertThat(service.rename(userId, categoryId, "Food").getName()).isEqualTo("Food");
        verify(categoryRepository, never()).existsByUserIdAndNameIgnoreCase(any(), any());
    }

    @Test
    void deleteRemovesTheCallersCategory() {
        Category category = new Category(userId, "Food");
        when(categoryRepository.findByIdAndUserId(categoryId, userId)).thenReturn(Optional.of(category));

        service.delete(userId, categoryId);

        verify(categoryRepository).delete(category);
    }

    @Test
    void aCategoryUsedByTransactionsCannotBeDeleted() {
        Category category = new Category(userId, "Food");
        when(categoryRepository.findByIdAndUserId(categoryId, userId)).thenReturn(Optional.of(category));
        when(transactionRepository.existsByCategoryId(categoryId)).thenReturn(true);

        assertThatThrownBy(() -> service.delete(userId, categoryId)).isInstanceOf(CategoryInUseException.class);
        verify(categoryRepository, never()).delete(any(Category.class));
    }

    @Test
    void deleteOfSomeoneElsesCategoryIsNotFoundAndDeletesNothing() {
        when(categoryRepository.findByIdAndUserId(categoryId, userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.delete(userId, categoryId)).isInstanceOf(ResourceNotFoundException.class);
        verify(categoryRepository, never()).delete(any(Category.class));
    }
}
