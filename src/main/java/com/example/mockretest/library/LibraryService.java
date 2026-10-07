package com.example.mockretest.library;

import com.example.mockretest.library.dto.BookRequest;
import com.example.mockretest.library.dto.BookResponse;
import com.example.mockretest.library.dto.LoanResponse;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
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
        Book book = findBook(id);
        if (!book.isAvailable()) {
            throw new BookUnavailableException(id);
        }
        loanRepository.deleteByBookId(id);
        bookRepository.delete(book);
    }

    @Transactional
    public LoanResponse borrow(long bookId, String memberId) {
        Book book = findBook(bookId);
        if (!book.isAvailable()) {
            throw new BookUnavailableException(bookId);
        }
        book.markBorrowed();
        try {
            // Flush now so a concurrent borrow of the same book fails the @Version check here.
            bookRepository.saveAndFlush(book);
        } catch (OptimisticLockingFailureException e) {
            throw new BookUnavailableException(bookId);
        }
        Loan loan = loanRepository.save(new Loan(book, memberId, Instant.now(clock)));
        return toResponse(loan);
    }

    @Transactional
    public LoanResponse returnBook(long bookId) {
        Book book = findBook(bookId);
        if (book.isAvailable()) {
            throw new BookNotBorrowedException(bookId);
        }
        Loan loan = loanRepository
                .findFirstByBookIdAndReturnedAtIsNull(bookId)
                .orElseThrow(() -> new BookNotBorrowedException(bookId));
        loan.markReturned(Instant.now(clock));
        book.markReturned();
        try {
            bookRepository.saveAndFlush(book);
        } catch (OptimisticLockingFailureException e) {
            throw new BookNotBorrowedException(bookId);
        }
        return toResponse(loan);
    }

    private Book findBook(long id) {
        return bookRepository.findById(id).orElseThrow(() -> new BookNotFoundException(id));
    }

    /** The pre-check gives a friendly error; the unique constraint is the real guard under concurrency. */
    private Book saveWithUniqueIsbn(Book book) {
        try {
            return bookRepository.saveAndFlush(book);
        } catch (DataIntegrityViolationException e) {
            throw new DuplicateIsbnException(book.getIsbn());
        }
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
