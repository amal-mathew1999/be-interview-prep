package com.example.mockretest.quotes;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class QuoteServiceTest {

    private final QuoteService service = new QuoteService();

    @Test
    void randomQuoteComesFromFixedList() {
        for (int i = 0; i < 50; i++) {
            Quote quote = service.randomQuote();
            assertThat(service.allQuotes()).contains(quote);
            assertThat(quote.text()).isNotBlank();
            assertThat(quote.author()).isNotBlank();
        }
    }

    @Test
    void fixedListIsNotEmpty() {
        assertThat(service.allQuotes()).isNotEmpty();
    }
}
