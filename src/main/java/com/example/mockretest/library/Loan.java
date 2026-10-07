package com.example.mockretest.library;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Optional;

@Entity
@Table(name = "library_loan")
public class Loan {

    public static final int MEMBER_ID_MAX_LENGTH = 255;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "book_id", nullable = false)
    private Book book;

    @Column(name = "member_id", nullable = false, length = MEMBER_ID_MAX_LENGTH)
    private String memberId;

    @Column(name = "borrowed_at", nullable = false)
    private Instant borrowedAt;

    @Column(name = "returned_at")
    private Instant returnedAt;

    protected Loan() {}

    public Loan(Book book, String memberId, Instant borrowedAt) {
        this.book = book;
        this.memberId = memberId;
        this.borrowedAt = borrowedAt;
    }

    public void markReturned(Instant at) {
        this.returnedAt = at;
    }

    public Long getId() {
        return id;
    }

    public Book getBook() {
        return book;
    }

    public String getMemberId() {
        return memberId;
    }

    public Instant getBorrowedAt() {
        return borrowedAt;
    }

    public Optional<Instant> getReturnedAt() {
        return Optional.ofNullable(returnedAt);
    }
}
