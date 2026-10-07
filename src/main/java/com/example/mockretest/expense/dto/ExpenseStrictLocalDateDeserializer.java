package com.example.mockretest.expense.dto;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.ValueDeserializer;

/**
 * Accepts only JSON strings in strict ISO {@code uuuu-MM-dd} form. Rejects date-times, offsets, epoch numbers,
 * arrays and impossible dates such as {@code 2026-02-30}.
 */
public class ExpenseStrictLocalDateDeserializer extends ValueDeserializer<LocalDate> {

    static final DateTimeFormatter STRICT_ISO_DATE =
            DateTimeFormatter.ofPattern("uuuu-MM-dd").withResolverStyle(ResolverStyle.STRICT);

    @Override
    public LocalDate deserialize(JsonParser parser, DeserializationContext ctxt) {
        if (!parser.hasToken(JsonToken.VALUE_STRING)) {
            return (LocalDate) ctxt.handleUnexpectedToken(LocalDate.class, parser);
        }
        String text = parser.getString();
        try {
            return LocalDate.parse(text, STRICT_ISO_DATE);
        } catch (DateTimeParseException ex) {
            return (LocalDate) ctxt.handleWeirdStringValue(LocalDate.class, text, "expected ISO date yyyy-MM-dd");
        }
    }
}
