package com.example.mockretest.library;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;

@DataJpaTest
class LibraryBookRepositoryTest {

    @Autowired
    private LibraryBookRepository repository;

    @Autowired
    private LibraryLoanRepository loanRepository;

    private static final long UNKNOWN_ID = Long.MAX_VALUE;

    private LibraryBook saveBook(String isbn) {
        return repository.saveAndFlush(new LibraryBook("Title " + isbn, "Author", isbn, null));
    }

    private long versionOf(long id) {
        return (long) ReflectionTestUtils.getField(repository.findById(id).orElseThrow(), "version");
    }

    private boolean isAvailable(long id) {
        return repository.findById(id).orElseThrow().isAvailable();
    }

    @Test
    void markBorrowedIfAvailableUpdatesOnceThenMatchesNothing() {
        long id = saveBook("borrow-1").getId();
        long before = versionOf(id);

        assertThat(repository.markBorrowedIfAvailable(id)).isEqualTo(1);
        assertThat(isAvailable(id)).isFalse();
        assertThat(versionOf(id)).isEqualTo(before + 1);

        assertThat(repository.markBorrowedIfAvailable(id)).isZero();
        assertThat(versionOf(id)).isEqualTo(before + 1);
    }

    @Test
    void markReturnedIfBorrowedUpdatesOnceThenMatchesNothing() {
        long id = saveBook("return-1").getId();
        assertThat(repository.markReturnedIfBorrowed(id)).isZero();
        repository.markBorrowedIfAvailable(id);
        long before = versionOf(id);

        assertThat(repository.markReturnedIfBorrowed(id)).isEqualTo(1);
        assertThat(isAvailable(id)).isTrue();
        assertThat(versionOf(id)).isEqualTo(before + 1);

        assertThat(repository.markReturnedIfBorrowed(id)).isZero();
        assertThat(versionOf(id)).isEqualTo(before + 1);
    }

    @Test
    void lockIfAvailableBumpsVersionOnlyForAvailableBook() {
        long id = saveBook("lock-1").getId();
        long before = versionOf(id);

        assertThat(repository.lockIfAvailable(id)).isEqualTo(1);
        assertThat(versionOf(id)).isEqualTo(before + 1);
        assertThat(isAvailable(id)).isTrue();

        repository.markBorrowedIfAvailable(id);
        long borrowedVersion = versionOf(id);
        assertThat(repository.lockIfAvailable(id)).isZero();
        assertThat(versionOf(id)).isEqualTo(borrowedVersion);
    }

    @Test
    void conditionalUpdatesAndDeleteMatchNothingForUnknownId() {
        assertThat(repository.markBorrowedIfAvailable(UNKNOWN_ID)).isZero();
        assertThat(repository.markReturnedIfBorrowed(UNKNOWN_ID)).isZero();
        assertThat(repository.lockIfAvailable(UNKNOWN_ID)).isZero();
        assertThat(repository.deleteBookById(UNKNOWN_ID)).isZero();
    }

    @Test
    void deleteBookByIdRemovesOnlyThatBook() {
        long doomed = saveBook("del-1").getId();
        long kept = saveBook("del-2").getId();

        assertThat(repository.deleteBookById(doomed)).isEqualTo(1);
        assertThat(repository.existsById(doomed)).isFalse();
        assertThat(repository.existsById(kept)).isTrue();
        assertThat(repository.deleteBookById(doomed)).isZero();
    }

    @Test
    void deleteByBookIdRemovesOnlyThatBooksLoans() {
        LibraryBook doomed = saveBook("loans-1");
        LibraryBook kept = saveBook("loans-2");
        Instant at = Instant.parse("2026-01-01T00:00:00Z");
        loanRepository.saveAndFlush(new LibraryLoan(doomed, "m1", at));
        loanRepository.saveAndFlush(new LibraryLoan(doomed, "m2", at));
        LibraryLoan keptLoan = loanRepository.saveAndFlush(new LibraryLoan(kept, "m3", at));

        loanRepository.deleteByBookId(doomed.getId());

        assertThat(loanRepository.findAll()).extracting(LibraryLoan::getId).containsExactly(keptLoan.getId());
    }

    @Test
    void enforcesUniqueIsbnAtDatabaseLevel() {
        repository.saveAndFlush(new LibraryBook("Dune", "Frank Herbert", "978-0441013593", 1965));

        assertThatThrownBy(() -> repository.saveAndFlush(new LibraryBook("Other", "Someone", "978-0441013593", null)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void duplicateIsbnViolationIsRecognisedAsIsbnConstraint() {
        repository.saveAndFlush(new LibraryBook("Dune", "Frank Herbert", "dup-isbn", 1965));

        assertThatThrownBy(() -> repository.saveAndFlush(new LibraryBook("Other", "Someone", "dup-isbn", null)))
                .isInstanceOfSatisfying(
                        DataIntegrityViolationException.class,
                        e -> assertThat(LibraryService.isIsbnUniqueViolation(e)).isTrue());
    }

    @Test
    void searchesByCaseInsensitiveTitleAndAuthorSubstrings() {
        repository.saveAndFlush(new LibraryBook("Dune", "Frank Herbert", "1", 1965));
        repository.saveAndFlush(new LibraryBook("Dune Messiah", "Frank Herbert", "2", 1969));
        repository.saveAndFlush(new LibraryBook("Emma", "Jane Austen", "3", 1815));

        assertThat(repository.findByTitleContainingIgnoreCaseAndAuthorContainingIgnoreCaseOrderByIdAsc("dUNE", ""))
                .extracting(LibraryBook::getIsbn)
                .containsExactly("1", "2");
        assertThat(repository.findByTitleContainingIgnoreCaseAndAuthorContainingIgnoreCaseOrderByIdAsc("", "AUSTEN"))
                .extracting(LibraryBook::getIsbn)
                .containsExactly("3");
        assertThat(repository.findByTitleContainingIgnoreCaseAndAuthorContainingIgnoreCaseOrderByIdAsc(
                        "messiah", "herbert"))
                .extracting(LibraryBook::getIsbn)
                .containsExactly("2");
        assertThat(repository.findByTitleContainingIgnoreCaseAndAuthorContainingIgnoreCaseOrderByIdAsc("", ""))
                .hasSize(3);
    }

    @Test
    void treatsLikeWildcardsInSearchTermsLiterally() {
        repository.saveAndFlush(new LibraryBook("100% Pure", "A", "1", null));
        repository.saveAndFlush(new LibraryBook("Plain", "B", "2", null));

        assertThat(repository.findByTitleContainingIgnoreCaseAndAuthorContainingIgnoreCaseOrderByIdAsc("%", ""))
                .extracting(LibraryBook::getIsbn)
                .containsExactly("1");
    }
}
