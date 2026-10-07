package com.example.mockretest.library;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;

@DataJpaTest
class BookRepositoryTest {

    @Autowired
    private BookRepository repository;

    @Test
    void enforcesUniqueIsbnAtDatabaseLevel() {
        repository.saveAndFlush(new Book("Dune", "Frank Herbert", "978-0441013593", 1965));

        assertThatThrownBy(() -> repository.saveAndFlush(new Book("Other", "Someone", "978-0441013593", null)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void searchesByCaseInsensitiveTitleAndAuthorSubstrings() {
        repository.saveAndFlush(new Book("Dune", "Frank Herbert", "1", 1965));
        repository.saveAndFlush(new Book("Dune Messiah", "Frank Herbert", "2", 1969));
        repository.saveAndFlush(new Book("Emma", "Jane Austen", "3", 1815));

        assertThat(repository.findByTitleContainingIgnoreCaseAndAuthorContainingIgnoreCaseOrderByIdAsc("dUNE", ""))
                .extracting(Book::getIsbn)
                .containsExactly("1", "2");
        assertThat(repository.findByTitleContainingIgnoreCaseAndAuthorContainingIgnoreCaseOrderByIdAsc("", "AUSTEN"))
                .extracting(Book::getIsbn)
                .containsExactly("3");
        assertThat(repository.findByTitleContainingIgnoreCaseAndAuthorContainingIgnoreCaseOrderByIdAsc(
                        "messiah", "herbert"))
                .extracting(Book::getIsbn)
                .containsExactly("2");
        assertThat(repository.findByTitleContainingIgnoreCaseAndAuthorContainingIgnoreCaseOrderByIdAsc("", ""))
                .hasSize(3);
    }

    @Test
    void treatsLikeWildcardsInSearchTermsLiterally() {
        repository.saveAndFlush(new Book("100% Pure", "A", "1", null));
        repository.saveAndFlush(new Book("Plain", "B", "2", null));

        assertThat(repository.findByTitleContainingIgnoreCaseAndAuthorContainingIgnoreCaseOrderByIdAsc("%", ""))
                .extracting(Book::getIsbn)
                .containsExactly("1");
    }
}
