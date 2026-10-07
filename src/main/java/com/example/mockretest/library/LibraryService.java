package com.example.mockretest.library;

import com.example.mockretest.library.dto.BookRequest;
import com.example.mockretest.library.dto.BookResponse;
import com.example.mockretest.library.dto.LoanResponse;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class LibraryService {

    private final BookRepository bookRepository;
    private final LoanRepository loanRepository;
    private final Clock clock;

    public LibraryService(
            BookRepository bookRepository, LoanRepository loanRepository, @Qualifier("libraryClock") Clock clock) {
        this.bookRepository = bookRepository;
        this.loanRepository = loanRepository;
        this.clock = clock;
    }

    public List<BookResponse> search(String title, String author) {
        return bookRepository
                .findByTitleContainingIgnoreCaseAndAuthorContainingIgnoreCaseOrderByIdAsc(
                        Objects.requireNonNullElse(title, ""), Objects.requireNonNullElse(author, ""))
                .stream()
                .map(LibraryService::toResponse)
                .toList();
    }

    public BookResponse get(long id) {
        return toResponse(findBook(id));
    }

    @Transactional
    public BookResponse create(BookRequest request) {
        if (bookRepository.existsByIsbn(request.isbn())) {
            throw new DuplicateIsbnException(request.isbn());
        }
        Book book = new Book(request.title(), request.author(), request.isbn(), request.publishedYear());
        return toResponse(saveWithUniqueIsbn(book));
    }

    @Transactional
    public BookResponse update(long id, BookRequest request) {
        Book book = findBook(id);
        if (bookRepository.existsByIsbnAndIdNot(request.isbn(), id)) {
            throw new DuplicateIsbnException(request.isbn());
        }
        book.replaceDetails(request.title(), request.author(), request.isbn(), request.publishedYear());
        return toResponse(saveWithUniqueIsbn(book));
    }

    @Transactional
    public void delete(long id) {
        // Conditional row update instead of read-then-check: a concurrent borrow cannot interleave.
        if (bookRepository.lockIfAvailable(id) == 0) {
            throw notFoundOr(id, new BookUnavailableException(id));
        }
        loanRepository.deleteByBookId(id);
        bookRepository.deleteBookById(id);
    }

    @Transactional
    public LoanResponse borrow(long bookId, String memberId) {
        // The availability check runs inside the UPDATE, so only a real borrow by someone else yields 409;
        // unrelated concurrent changes (e.g. a PUT) no longer cause a false "currently borrowed".
        if (bookRepository.markBorrowedIfAvailable(bookId) == 0) {
            throw notFoundOr(bookId, new BookUnavailableException(bookId));
        }
        Book book = findBook(bookId);
        Loan loan = loanRepository.save(new Loan(book, memberId, Instant.now(clock)));
        return toResponse(loan);
    }

    @Transactional
    public LoanResponse returnBook(long bookId) {
        if (bookRepository.markReturnedIfBorrowed(bookId) == 0) {
            throw notFoundOr(bookId, new BookNotBorrowedException(bookId));
        }
        Loan loan = loanRepository
                .findFirstByBookIdAndReturnedAtIsNull(bookId)
                .orElseThrow(() -> new BookNotBorrowedException(bookId));
        loan.markReturned(Instant.now(clock));
        return toResponse(loan);
    }

    private Book findBook(long id) {
        return bookRepository.findById(id).orElseThrow(() -> new BookNotFoundException(id));
    }

    /** After a conditional update matched no row, re-reads the committed state to pick the accurate error. */
    private RuntimeException notFoundOr(long id, RuntimeException conflict) {
        return bookRepository.existsById(id) ? conflict : new BookNotFoundException(id);
    }

    /** The pre-check gives a friendly error; the unique constraint is the real guard under concurrency. */
    private Book saveWithUniqueIsbn(Book book) {
        try {
            return bookRepository.saveAndFlush(book);
        } catch (DataIntegrityViolationException e) {
            if (isIsbnUniqueViolation(e)) {
                throw new DuplicateIsbnException(book.getIsbn());
            }
            throw e;
        }
    }

    /** True only when the violated constraint is the ISBN unique constraint; other violations are not conflicts. */
    static boolean isIsbnUniqueViolation(DataIntegrityViolationException e) {
        for (Throwable cause = e; cause != null; cause = cause.getCause()) {
            if (cause instanceof ConstraintViolationException violation) {
                String name = violation.getConstraintName();
                return name != null && name.toLowerCase(Locale.ROOT).contains(Book.ISBN_UNIQUE_CONSTRAINT);
            }
        }
        return false;
    }

    private static BookResponse toResponse(Book book) {
        return new BookResponse(
                book.getId(),
                book.getTitle(),
                book.getAuthor(),
                book.getIsbn(),
                book.getPublishedYear().orElse(null),
                book.isAvailable());
    }

    private static LoanResponse toResponse(Loan loan) {
        return new LoanResponse(
                loan.getId(),
                loan.getBook().getId(),
                loan.getMemberId(),
                loan.getBorrowedAt(),
                loan.getReturnedAt().orElse(null));
    }
}
