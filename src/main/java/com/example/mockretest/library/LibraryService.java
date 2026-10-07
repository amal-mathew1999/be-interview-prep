package com.example.mockretest.library;

import com.example.mockretest.library.dto.LibraryBookRequest;
import com.example.mockretest.library.dto.LibraryBookResponse;
import com.example.mockretest.library.dto.LibraryLoanResponse;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class LibraryService {

    private final LibraryBookRepository bookRepository;
    private final LibraryLoanRepository loanRepository;
    private final LibraryClock clock;

    public LibraryService(
            LibraryBookRepository bookRepository, LibraryLoanRepository loanRepository, LibraryClock clock) {
        this.bookRepository = bookRepository;
        this.loanRepository = loanRepository;
        this.clock = clock;
    }

    public List<LibraryBookResponse> search(String title, String author) {
        return bookRepository
                .findByTitleContainingIgnoreCaseAndAuthorContainingIgnoreCaseOrderByIdAsc(
                        Objects.requireNonNullElse(title, ""), Objects.requireNonNullElse(author, ""))
                .stream()
                .map(LibraryService::toResponse)
                .toList();
    }

    public LibraryBookResponse get(long id) {
        return toResponse(findBook(id));
    }

    @Transactional
    public LibraryBookResponse create(LibraryBookRequest request) {
        if (bookRepository.existsByIsbn(request.isbn())) {
            throw new LibraryDuplicateIsbnException(request.isbn());
        }
        LibraryBook book = new LibraryBook(request.title(), request.author(), request.isbn(), request.publishedYear());
        return toResponse(saveWithUniqueIsbn(book));
    }

    @Transactional
    public LibraryBookResponse update(long id, LibraryBookRequest request) {
        LibraryBook book = findBook(id);
        if (bookRepository.existsByIsbnAndIdNot(request.isbn(), id)) {
            throw new LibraryDuplicateIsbnException(request.isbn());
        }
        book.replaceDetails(request.title(), request.author(), request.isbn(), request.publishedYear());
        return toResponse(saveWithUniqueIsbn(book));
    }

    @Transactional
    public void delete(long id) {
        // Conditional row update instead of read-then-check: a concurrent borrow cannot interleave.
        if (bookRepository.lockIfAvailable(id) == 0) {
            throw notFoundOr(id, new LibraryBookUnavailableException(id));
        }
        loanRepository.deleteByBookId(id);
        bookRepository.deleteBookById(id);
    }

    @Transactional
    public LibraryLoanResponse borrow(long bookId, String memberId) {
        // The availability check runs inside the UPDATE, so only a real borrow by someone else yields 409;
        // unrelated concurrent changes (e.g. a PUT) no longer cause a false "currently borrowed".
        if (bookRepository.markBorrowedIfAvailable(bookId) == 0) {
            throw notFoundOr(bookId, new LibraryBookUnavailableException(bookId));
        }
        LibraryBook book = findBook(bookId);
        LibraryLoan loan = loanRepository.save(new LibraryLoan(book, memberId, clock.now()));
        return toResponse(loan);
    }

    @Transactional
    public LibraryLoanResponse returnBook(long bookId) {
        if (bookRepository.markReturnedIfBorrowed(bookId) == 0) {
            throw notFoundOr(bookId, new LibraryBookNotBorrowedException(bookId));
        }
        LibraryLoan loan = loanRepository
                .findFirstByBookIdAndReturnedAtIsNull(bookId)
                .orElseThrow(() -> new LibraryBookNotBorrowedException(bookId));
        loan.markReturned(clock.now());
        return toResponse(loan);
    }

    private LibraryBook findBook(long id) {
        return bookRepository.findById(id).orElseThrow(() -> new LibraryBookNotFoundException(id));
    }

    /** After a conditional update matched no row, re-reads the committed state to pick the accurate error. */
    private RuntimeException notFoundOr(long id, RuntimeException conflict) {
        return bookRepository.existsById(id) ? conflict : new LibraryBookNotFoundException(id);
    }

    /** The pre-check gives a friendly error; the unique constraint is the real guard under concurrency. */
    private LibraryBook saveWithUniqueIsbn(LibraryBook book) {
        try {
            return bookRepository.saveAndFlush(book);
        } catch (DataIntegrityViolationException e) {
            if (isIsbnUniqueViolation(e)) {
                throw new LibraryDuplicateIsbnException(book.getIsbn());
            }
            throw e;
        }
    }

    /** True only when the violated constraint is the ISBN unique constraint; other violations are not conflicts. */
    static boolean isIsbnUniqueViolation(DataIntegrityViolationException e) {
        for (Throwable cause = e; cause != null; cause = cause.getCause()) {
            if (cause instanceof ConstraintViolationException violation) {
                String name = violation.getConstraintName();
                return name != null && name.toLowerCase(Locale.ROOT).contains(LibraryBook.ISBN_UNIQUE_CONSTRAINT);
            }
        }
        return false;
    }

    private static LibraryBookResponse toResponse(LibraryBook book) {
        return new LibraryBookResponse(
                book.getId(),
                book.getTitle(),
                book.getAuthor(),
                book.getIsbn(),
                book.getPublishedYear().orElse(null),
                book.isAvailable());
    }

    private static LibraryLoanResponse toResponse(LibraryLoan loan) {
        return new LibraryLoanResponse(
                loan.getId(),
                loan.getBook().getId(),
                loan.getMemberId(),
                loan.getBorrowedAt(),
                loan.getReturnedAt().orElse(null));
    }
}
