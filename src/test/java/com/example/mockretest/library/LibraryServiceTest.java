package com.example.mockretest.library;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.mockretest.library.dto.BookRequest;
import com.example.mockretest.library.dto.BookResponse;
import com.example.mockretest.library.dto.LoanResponse;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class LibraryServiceTest {

    private static final Instant NOW = Instant.parse("2026-03-01T10:00:00Z");

    @Mock
    private BookRepository bookRepository;

    @Mock
    private LoanRepository loanRepository;

    private LibraryService service;

    @BeforeEach
    void setUp() {
        service =
                new LibraryService(bookRepository, loanRepository, new LibraryClock(Clock.fixed(NOW, ZoneOffset.UTC)));
    }

    private static BookRequest request(String isbn) {
        return new BookRequest("Dune", "Frank Herbert", isbn, 1965);
    }

    private static Book book(long id, String isbn) {
        Book book = new Book("Dune", "Frank Herbert", isbn, 1965);
        ReflectionTestUtils.setField(book, "id", id);
        return book;
    }

    @Test
    void createsAvailableBook() {
        when(bookRepository.existsByIsbn("123")).thenReturn(false);
        when(bookRepository.saveAndFlush(any(Book.class))).thenAnswer(inv -> {
            Book saved = inv.getArgument(0);
            ReflectionTestUtils.setField(saved, "id", 1L);
            return saved;
        });

        BookResponse response = service.create(request("123"));

        assertThat(response).isEqualTo(new BookResponse(1L, "Dune", "Frank Herbert", "123", 1965, true));
    }

    @Test
    void rejectsDuplicateIsbnOnCreateViaPreCheck() {
        when(bookRepository.existsByIsbn("123")).thenReturn(true);

        assertThatThrownBy(() -> service.create(request("123")))
                .isInstanceOf(DuplicateIsbnException.class)
                .hasMessageContaining("123");
        verify(bookRepository, never()).saveAndFlush(any());
    }

    private static DataIntegrityViolationException violationOf(String constraintName) {
        return new DataIntegrityViolationException(
                "could not execute statement",
                new ConstraintViolationException("violation", new SQLException("db"), constraintName));
    }

    @Test
    void translatesUniqueConstraintViolationOnCreateToDuplicateIsbn() {
        when(bookRepository.existsByIsbn("123")).thenReturn(false);
        when(bookRepository.saveAndFlush(any(Book.class)))
                .thenThrow(violationOf("PUBLIC.UK_LIBRARY_BOOK_ISBN_INDEX_8 ON PUBLIC.LIBRARY_BOOK(ISBN)"));

        assertThatThrownBy(() -> service.create(request("123"))).isInstanceOf(DuplicateIsbnException.class);
    }

    @Test
    void rethrowsUnrelatedConstraintViolationOnCreate() {
        DataIntegrityViolationException unrelated = violationOf("FK_SOMETHING_ELSE");
        when(bookRepository.existsByIsbn("123")).thenReturn(false);
        when(bookRepository.saveAndFlush(any(Book.class))).thenThrow(unrelated);

        assertThatThrownBy(() -> service.create(request("123"))).isSameAs(unrelated);
    }

    @Test
    void rethrowsDataIntegrityViolationWithoutConstraintName() {
        DataIntegrityViolationException unknown = new DataIntegrityViolationException("value too long");
        when(bookRepository.existsByIsbn("123")).thenReturn(false);
        when(bookRepository.saveAndFlush(any(Book.class))).thenThrow(unknown);

        assertThatThrownBy(() -> service.create(request("123"))).isSameAs(unknown);
    }

    @Test
    void updatesEditableFields() {
        Book existing = book(1L, "123");
        when(bookRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(bookRepository.existsByIsbnAndIdNot("999", 1L)).thenReturn(false);
        when(bookRepository.saveAndFlush(existing)).thenReturn(existing);

        BookResponse response = service.update(1L, new BookRequest("Emma", "Jane Austen", "999", null));

        assertThat(response).isEqualTo(new BookResponse(1L, "Emma", "Jane Austen", "999", null, true));
    }

    @Test
    void rejectsUpdateWithIsbnOfAnotherBook() {
        when(bookRepository.findById(1L)).thenReturn(Optional.of(book(1L, "123")));
        when(bookRepository.existsByIsbnAndIdNot("999", 1L)).thenReturn(true);

        assertThatThrownBy(() -> service.update(1L, request("999"))).isInstanceOf(DuplicateIsbnException.class);
    }

    @Test
    void translatesUniqueConstraintViolationOnUpdateToDuplicateIsbn() {
        Book existing = book(1L, "123");
        when(bookRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(bookRepository.existsByIsbnAndIdNot("999", 1L)).thenReturn(false);
        when(bookRepository.saveAndFlush(existing)).thenThrow(violationOf("uk_library_book_isbn"));

        assertThatThrownBy(() -> service.update(1L, request("999"))).isInstanceOf(DuplicateIsbnException.class);
    }

    @Test
    void throwsNotFoundForUnknownBook() {
        when(bookRepository.findById(42L)).thenReturn(Optional.empty());
        when(bookRepository.lockIfAvailable(42L)).thenReturn(0);
        when(bookRepository.markBorrowedIfAvailable(42L)).thenReturn(0);
        when(bookRepository.markReturnedIfBorrowed(42L)).thenReturn(0);
        when(bookRepository.existsById(42L)).thenReturn(false);

        assertThatThrownBy(() -> service.get(42L)).isInstanceOf(BookNotFoundException.class);
        assertThatThrownBy(() -> service.update(42L, request("1"))).isInstanceOf(BookNotFoundException.class);
        assertThatThrownBy(() -> service.delete(42L)).isInstanceOf(BookNotFoundException.class);
        assertThatThrownBy(() -> service.borrow(42L, "m1")).isInstanceOf(BookNotFoundException.class);
        assertThatThrownBy(() -> service.returnBook(42L)).isInstanceOf(BookNotFoundException.class);
    }

    @Test
    void searchTreatsMissingFiltersAsMatchAll() {
        when(bookRepository.findByTitleContainingIgnoreCaseAndAuthorContainingIgnoreCaseOrderByIdAsc("", "herb"))
                .thenReturn(List.of(book(1L, "123")));

        assertThat(service.search(null, "herb")).extracting(BookResponse::id).containsExactly(1L);
    }

    @Test
    void borrowsAvailableBook() {
        Book existing = book(1L, "123");
        when(bookRepository.markBorrowedIfAvailable(1L)).thenReturn(1);
        when(bookRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(loanRepository.save(any(Loan.class))).thenAnswer(inv -> {
            Loan loan = inv.getArgument(0);
            ReflectionTestUtils.setField(loan, "id", 7L);
            return loan;
        });

        LoanResponse loan = service.borrow(1L, "member-1");

        assertThat(loan).isEqualTo(new LoanResponse(7L, 1L, "member-1", NOW, null));
    }

    @Test
    void rejectsBorrowingBorrowedBook() {
        when(bookRepository.markBorrowedIfAvailable(1L)).thenReturn(0);
        when(bookRepository.existsById(1L)).thenReturn(true);

        assertThatThrownBy(() -> service.borrow(1L, "member-2"))
                .isInstanceOf(BookUnavailableException.class)
                .hasMessageContaining("currently borrowed");
        verify(loanRepository, never()).save(any());
    }

    @Test
    void returnsBorrowedBook() {
        Book existing = book(1L, "123");
        Loan loan = new Loan(existing, "member-1", NOW.minusSeconds(60));
        ReflectionTestUtils.setField(loan, "id", 7L);
        when(bookRepository.markReturnedIfBorrowed(1L)).thenReturn(1);
        when(loanRepository.findFirstByBookIdAndReturnedAtIsNull(1L)).thenReturn(Optional.of(loan));

        LoanResponse response = service.returnBook(1L);

        assertThat(response).isEqualTo(new LoanResponse(7L, 1L, "member-1", NOW.minusSeconds(60), NOW));
    }

    @Test
    void rejectsReturningBookThatIsNotBorrowed() {
        when(bookRepository.markReturnedIfBorrowed(1L)).thenReturn(0);
        when(bookRepository.existsById(1L)).thenReturn(true);

        assertThatThrownBy(() -> service.returnBook(1L)).isInstanceOf(BookNotBorrowedException.class);
    }

    @Test
    void deletesAvailableBookAndItsLoanHistory() {
        when(bookRepository.lockIfAvailable(1L)).thenReturn(1);

        service.delete(1L);

        verify(loanRepository).deleteByBookId(1L);
        verify(bookRepository).deleteBookById(1L);
    }

    @Test
    void rejectsDeletingBorrowedBook() {
        when(bookRepository.lockIfAvailable(1L)).thenReturn(0);
        when(bookRepository.existsById(1L)).thenReturn(true);

        assertThatThrownBy(() -> service.delete(1L))
                .isInstanceOf(BookUnavailableException.class)
                .hasMessageContaining("currently borrowed");
        verify(loanRepository, never()).deleteByBookId(anyLong());
        verify(bookRepository, never()).deleteBookById(anyLong());
    }
}
