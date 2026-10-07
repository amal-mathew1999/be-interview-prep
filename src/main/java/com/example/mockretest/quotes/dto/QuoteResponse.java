package com.example.mockretest.quotes.dto;

import com.example.mockretest.quotes.Quote;

/** API representation of a quote. */
public record QuoteResponse(String text, String author) {

    public static QuoteResponse from(Quote quote) {
        return new QuoteResponse(quote.text(), quote.author());
    }
}
