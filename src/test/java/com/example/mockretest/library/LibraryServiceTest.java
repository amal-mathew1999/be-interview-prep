package com.example.mockretest.library;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.mockretest.library.dto.BookRequest;
import com.example.mockretest.library.dto.BookResponse;
import com.example.mockretest.library.dto.LoanResponse;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
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
        service = new LibraryService(bookRepository, loanRepository, Clock.fixed(NOW, ZoneOffset.UTC));
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

    @Test
    void translatesUniqueConstraintViolationOnCreateToDuplicateIsbn() {
        when(bookRepository.existsByIsbn("123")).thenReturn(false);
        when(bookRepository.saveAndFlush(any(Book.class))).thenThrow(new DataIntegrityViolationException("unique"));

        assertThatThrownBy(() -> service.create(request("123"))).isInstanceOf(DuplicateIsbnException.class);
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
        when(bookRepository.saveAndFlush(existing)).thenThrow(new DataIntegrityViolationException("unique"));

        assertThatThrownBy(() -> service.update(1L, request("999"))).isInstanceOf(DuplicateIsbnException.class);
    }

    @Test
    void throwsNotFoundForUnknownBook() {
        when(bookRepository.findById(42L)).thenReturn(Optional.empty());

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
        when(bookRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(bookRepository.saveAndFlush(existing)).thenReturn(existing);
        when(loanRepository.save(any(Loan.class))).thenAnswer(inv -> {
            Loan loan = inv.getArgument(0);
            ReflectionTestUtils.setField(loan, "id", 7L);
            return loan;
        });

        LoanResponse loan = service.borrow(1L, "member-1");

        assertThat(loan).isEqualTo(new LoanResponse(7L, 1L, "member-1", NOW, null));
        assertThat(existing.isAvailable()).isFalse();
    }

    @Test
    void rejectsBorrowingBorrowedBook() {
        Book existing = book(1L, "123");
        existing.markBorrowed();
        when(bookRepository.findById(1L)).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> service.borrow(1L, "member-2"))
                .isInstanceOf(BookUnavailableException.class)
                .hasMessageContaining("currently borrowed");
        verify(loanRepository, never()).save(any());
    }

    @Test
    void translatesConcurrentBorrowToBookUnavailable() {
        Book existing = book(1L, "123");
        when(bookRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(bookRepository.saveAndFlush(existing))
                .thenThrow(new ObjectOptimisticLockingFailureException(Book.class, 1L));

        assertThatThrownBy(() -> service.borrow(1L, "member-1")).isInstanceOf(BookUnavailableException.class);
        verify(loanRepository, never()).save(any());
    }

    @Test
    void returnsBorrowedBook() {
        Book existing = book(1L, "123");
        existing.markBorrowed();
        Loan loan = new Loan(existing, "member-1", NOW.minusSeconds(60));
        ReflectionTestUtils.setField(loan, "id", 7L);
        when(bookRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(loanRepository.findFirstByBookIdAndReturnedAtIsNull(1L)).thenReturn(Optional.of(loan));
        when(bookRepository.saveAndFlush(existing)).thenReturn(existing);

        LoanResponse response = service.returnBook(1L);

        assertThat(response).isEqualTo(new LoanResponse(7L, 1L, "member-1", NOW.minusSeconds(60), NOW));
        assertThat(existing.isAvailable()).isTrue();
    }

    @Test
    void rejectsReturningBookThatIsNotBorrowed() {
        when(bookRepository.findById(1L)).thenReturn(Optional.of(book(1L, "123")));

        assertThatThrownBy(() -> service.returnBook(1L)).isInstanceOf(BookNotBorrowedException.class);
    }

    @Test
    void deletesAvailableBookAndItsLoanHistory() {
        Book existing = book(1L, "123");
        when(bookRepository.findById(1L)).thenReturn(Optional.of(existing));

        service.delete(1L);

        verify(loanRepository).deleteByBookId(1L);
        verify(bookRepository).delete(existing);
    }

    @Test
    void rejectsDeletingBorrowedBook() {
        Book existing = book(1L, "123");
        existing.markBorrowed();
        when(bookRepository.findById(1L)).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> service.delete(1L))
                .isInstanceOf(BookUnavailableException.class)
                .hasMessageContaining("currently borrowed");
        verify(bookRepository, never()).delete(any());
    }
}
