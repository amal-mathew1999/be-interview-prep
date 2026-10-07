package com.example.mockretest.library;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.mockretest.library.dto.LibraryBookRequest;
import java.util.UUID;
import java.util.function.Consumer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Deterministic interleavings of competing transactions against the real database (H2).
 *
 * <p>Each test opens an outer transaction that first observes the book as available (so any application-level
 * read-then-check would pass), then lets a second, independent transaction ({@code REQUIRES_NEW}) commit a
 * conflicting change, and only then performs its own write. The outcome therefore depends solely on the guard the
 * database evaluates at write time, not on thread scheduling.
 */
@SpringBootTest
class LibraryConcurrencyIntegrationTest {

    @Autowired
    private LibraryService service;

    @Autowired
    private LibraryBookRepository bookRepository;

    @Autowired
    private LibraryLoanRepository loanRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private TransactionTemplate first;
    private TransactionTemplate second;

    @BeforeEach
    void setUp() {
        first = new TransactionTemplate(transactionManager);
        second = new TransactionTemplate(transactionManager);
        second.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    private long createAvailableBook() {
        String isbn = "cc-" + UUID.randomUUID().toString().substring(0, 20);
        return bookRepository
                .saveAndFlush(new LibraryBook("Contended", "Author", isbn, null))
                .getId();
    }

    /** Runs {@code ownWrite} in a transaction that saw the book available before {@code competing} committed. */
    private void interleave(long bookId, Runnable competing, Consumer<Long> ownWrite) {
        first.executeWithoutResult(status -> {
            assertThat(bookRepository.findById(bookId))
                    .hasValueSatisfying(book -> assertThat(book.isAvailable()).isTrue());
            second.executeWithoutResult(inner -> competing.run());
            ownWrite.accept(bookId);
            convertGlobalRollbackOnlyToLocal(status);
        });
    }

    /**
     * Converts a <em>global</em> rollback-only mark into a <em>local</em> one so the outer transaction rolls back
     * quietly instead of failing its commit.
     *
     * <p>When the service's inner {@code @Transactional} call rejects the write, it joins this outer transaction and
     * marks the shared resource (the JPA transaction) rollback-only — the global flag. {@link
     * TransactionStatus#isRollbackOnly()} reports that global flag too, but {@code TransactionTemplate} only checks
     * the <em>local</em> flag before deciding to commit; committing a globally rollback-only transaction would throw
     * {@code UnexpectedRollbackException}. Calling {@link TransactionStatus#setRollbackOnly()} sets the local flag,
     * so the template performs a plain rollback.
     */
    private static void convertGlobalRollbackOnlyToLocal(TransactionStatus status) {
        boolean globalOrLocalRollbackOnly = status.isRollbackOnly();
        if (globalOrLocalRollbackOnly) {
            status.setRollbackOnly(); // local flag: TransactionTemplate now rolls back instead of committing
        }
    }

    @Test
    void secondBorrowOfSameBookIsRejectedEvenIfItSawTheBookAvailable() {
        long id = createAvailableBook();

        interleave(id, () -> service.borrow(id, "winner"), bookId -> assertThatThrownBy(
                        () -> service.borrow(bookId, "loser"))
                .isInstanceOf(LibraryBookUnavailableException.class)
                .hasMessageContaining("currently borrowed"));

        assertThat(bookRepository.findById(id))
                .hasValueSatisfying(book -> assertThat(book.isAvailable()).isFalse());
        assertThat(loanRepository.findFirstByBookIdAndReturnedAtIsNull(id))
                .hasValueSatisfying(loan -> assertThat(loan.getMemberId()).isEqualTo("winner"));
    }

    @Test
    void deleteIsRejectedWhenBookWasBorrowedAfterItSawTheBookAvailable() {
        long id = createAvailableBook();

        interleave(id, () -> service.borrow(id, "borrower"), bookId -> assertThatThrownBy(() -> service.delete(bookId))
                .isInstanceOf(LibraryBookUnavailableException.class));

        assertThat(bookRepository.findById(id))
                .hasValueSatisfying(book -> assertThat(book.isAvailable()).isFalse());
        assertThat(loanRepository.findFirstByBookIdAndReturnedAtIsNull(id))
                .hasValueSatisfying(loan -> assertThat(loan.getMemberId()).isEqualTo("borrower"));
    }

    @Test
    void borrowSucceedsWhenBookWasEditedConcurrently() {
        long id = createAvailableBook();

        interleave(
                id,
                () -> service.update(id, new LibraryBookRequest("Edited", "Author", "cc-" + id + "-edited", null)),
                bookId -> service.borrow(bookId, "member"));

        assertThat(bookRepository.findById(id)).hasValueSatisfying(book -> {
            assertThat(book.getTitle()).isEqualTo("Edited");
            assertThat(book.isAvailable()).isFalse();
        });
        assertThat(loanRepository.findFirstByBookIdAndReturnedAtIsNull(id)).isPresent();
    }

    @Test
    void borrowReturnsNotFoundWhenBookWasDeletedConcurrently() {
        long id = createAvailableBook();

        interleave(id, () -> service.delete(id), bookId -> assertThatThrownBy(() -> service.borrow(bookId, "member"))
                .isInstanceOf(LibraryBookNotFoundException.class));

        assertThat(bookRepository.findById(id)).isEmpty();
    }

    @Test
    void secondReturnOfSameLoanIsRejected() {
        long id = createAvailableBook();
        service.borrow(id, "member");

        first.executeWithoutResult(status -> {
            second.executeWithoutResult(inner -> service.returnBook(id));
            assertThatThrownBy(() -> service.returnBook(id)).isInstanceOf(LibraryBookNotBorrowedException.class);
            convertGlobalRollbackOnlyToLocal(status);
        });

        assertThat(bookRepository.findById(id))
                .hasValueSatisfying(book -> assertThat(book.isAvailable()).isTrue());
    }
}
