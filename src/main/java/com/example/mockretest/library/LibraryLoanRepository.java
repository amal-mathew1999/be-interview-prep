package com.example.mockretest.library;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface LibraryLoanRepository extends JpaRepository<LibraryLoan, Long> {

    Optional<LibraryLoan> findFirstByBookIdAndReturnedAtIsNull(Long bookId);

    @Modifying
    @Query("delete from LibraryLoan l where l.book.id = :bookId")
    void deleteByBookId(@Param("bookId") Long bookId);
}
