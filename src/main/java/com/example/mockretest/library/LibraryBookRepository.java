package com.example.mockretest.library;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface LibraryBookRepository extends JpaRepository<LibraryBook, Long> {

    boolean existsByIsbn(String isbn);

    boolean existsByIsbnAndIdNot(String isbn, Long id);

    List<LibraryBook> findByTitleContainingIgnoreCaseAndAuthorContainingIgnoreCaseOrderByIdAsc(
            String title, String author);

    /**
     * Atomically flips an available book to borrowed. The availability condition is evaluated by the database
     * under the row lock, so of two concurrent borrows exactly one updates a row. Bumps the version so that
     * concurrent entity-based writes (update/delete) still fail their optimistic check.
     *
     * @return number of rows updated: 1 if the book was available, 0 if it is borrowed or does not exist
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(
            "update LibraryBook b set b.available = false, b.version = b.version + 1 where b.id = :id and b.available = true")
    int markBorrowedIfAvailable(@Param("id") long id);

    /**
     * Atomically flips a borrowed book back to available.
     *
     * @return number of rows updated: 1 if the book was borrowed, 0 if it is available or does not exist
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(
            "update LibraryBook b set b.available = true, b.version = b.version + 1 where b.id = :id and b.available = false")
    int markReturnedIfBorrowed(@Param("id") long id);

    /**
     * Locks the row of an available book (by bumping its version) so a concurrent borrow cannot slip in before it
     * is deleted.
     *
     * @return number of rows updated: 1 if the book was available, 0 if it is borrowed or does not exist
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update LibraryBook b set b.version = b.version + 1 where b.id = :id and b.available = true")
    int lockIfAvailable(@Param("id") long id);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from LibraryBook b where b.id = :id")
    int deleteBookById(@Param("id") long id);
}
