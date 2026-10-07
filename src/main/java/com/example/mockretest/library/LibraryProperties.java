package com.example.mockretest.library;

import java.time.ZoneId;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/** Configuration for the library feature, bound from {@code library.properties}. */
@ConfigurationProperties(prefix = "library")
public record LibraryProperties(@DefaultValue("UTC") ZoneId zoneId) {}
