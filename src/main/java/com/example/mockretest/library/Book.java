package com.example.mockretest.library;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import java.util.Optional;

@Entity
@Table(
        name = "library_book",
        uniqueConstraints = @UniqueConstraint(name = "uk_library_book_isbn", columnNames = "isbn"))
public class Book {

    public static final int TITLE_MAX_LENGTH = 255;
    public static final int AUTHOR_MAX_LENGTH = 255;
    public static final int ISBN_MAX_LENGTH = 32;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = TITLE_MAX_LENGTH)
    private String title;

    @Column(nullable = false, length = AUTHOR_MAX_LENGTH)
    private String author;

    @Column(nullable = false, length = ISBN_MAX_LENGTH)
    private String isbn;

    @Column(name = "published_year")
    private Integer publishedYear;

    @Column(nullable = false)
    private boolean available = true;

    /** Optimistic lock: concurrent borrow/return/update/delete of the same book cannot both commit. */
    @Version
    private long version;

    protected Book() {}

    public Book(String title, String author, String isbn, Integer publishedYear) {
        replaceDetails(title, author, isbn, publishedYear);
    }

    public final void replaceDetails(String title, String author, String isbn, Integer publishedYear) {
        this.title = title;
        this.author = author;
        this.isbn = isbn;
        this.publishedYear = publishedYear;
    }

    public void markBorrowed() {
        this.available = false;
    }

    public void markReturned() {
        this.available = true;
    }

    public Long getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }

    public String getAuthor() {
        return author;
    }

    public String getIsbn() {
        return isbn;
    }

    public Optional<Integer> getPublishedYear() {
        return Optional.ofNullable(publishedYear);
    }

    public boolean isAvailable() {
        return available;
    }
}
