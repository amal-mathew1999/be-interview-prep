package com.example.mockretest.library;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BookRepository extends JpaRepository<Book, Long> {

    boolean existsByIsbn(String isbn);

    boolean existsByIsbnAndIdNot(String isbn, Long id);

    List<Book> findByTitleContainingIgnoreCaseAndAuthorContainingIgnoreCaseOrderByIdAsc(String title, String author);
}
