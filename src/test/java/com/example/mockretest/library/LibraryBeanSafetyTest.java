package com.example.mockretest.library;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/** Guards against library beans that would break the merged application. */
class LibraryBeanSafetyTest {

    private final ApplicationContextRunner contextRunner =
            new ApplicationContextRunner().withUserConfiguration(LibraryConfig.class);

    @Test
    void contributesNoBeanAssignableToJavaTimeClock() {
        contextRunner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).doesNotHaveBean(Clock.class);
            assertThat(context).hasSingleBean(LibraryClock.class).hasBean("libraryClock");
        });
    }
}
