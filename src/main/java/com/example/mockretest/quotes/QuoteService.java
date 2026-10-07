package com.example.mockretest.quotes;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import org.springframework.stereotype.Service;

/** Serves quotes from a fixed in-memory list. */
@Service
public class QuoteService {

    private static final List<Quote> QUOTES = List.of(
            new Quote("Simplicity is prerequisite for reliability.", "Edsger W. Dijkstra"),
            new Quote("Premature optimization is the root of all evil.", "Donald Knuth"),
            new Quote("Talk is cheap. Show me the code.", "Linus Torvalds"),
            new Quote("Programs must be written for people to read.", "Harold Abelson"),
            new Quote("Make it work, make it right, make it fast.", "Kent Beck"));

    public Quote randomQuote() {
        return QUOTES.get(ThreadLocalRandom.current().nextInt(QUOTES.size()));
    }

    /** The full fixed list; package-private, used only to verify {@link #randomQuote()}. */
    List<Quote> allQuotes() {
        return QUOTES;
    }
}
