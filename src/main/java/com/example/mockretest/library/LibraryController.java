package com.example.mockretest.library;

import com.example.mockretest.library.dto.LibraryBookRequest;
import com.example.mockretest.library.dto.LibraryBookResponse;
import com.example.mockretest.library.dto.LibraryBorrowRequest;
import com.example.mockretest.library.dto.LibraryLoanResponse;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(LibraryController.BASE_PATH)
public class LibraryController {

    static final String BASE_PATH = "/api/books";

    private final LibraryService service;

    public LibraryController(LibraryService service) {
        this.service = service;
    }

    @GetMapping
    public List<LibraryBookResponse> list(
            @RequestParam(required = false) String title, @RequestParam(required = false) String author) {
        return service.search(title, author);
    }

    @GetMapping("/{id}")
    public LibraryBookResponse get(@PathVariable long id) {
        return service.get(id);
    }

    @PostMapping
    public ResponseEntity<LibraryBookResponse> create(@Valid @RequestBody LibraryBookRequest request) {
        LibraryBookResponse created = service.create(request);
        return ResponseEntity.created(URI.create(BASE_PATH + "/" + created.id()))
                .body(created);
    }

    @PutMapping("/{id}")
    public LibraryBookResponse update(@PathVariable long id, @Valid @RequestBody LibraryBookRequest request) {
        return service.update(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable long id) {
        service.delete(id);
    }

    @PostMapping("/{id}/borrow")
    public LibraryLoanResponse borrow(@PathVariable long id, @Valid @RequestBody LibraryBorrowRequest request) {
        return service.borrow(id, request.memberId());
    }

    @PostMapping("/{id}/return")
    public LibraryLoanResponse returnBook(@PathVariable long id) {
        return service.returnBook(id);
    }
}
