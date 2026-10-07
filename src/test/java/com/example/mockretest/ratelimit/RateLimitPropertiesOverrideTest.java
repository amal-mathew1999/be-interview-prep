package com.example.mockretest.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.scheduling.config.FixedDelayTask;
import org.springframework.scheduling.config.ScheduledTask;
import org.springframework.scheduling.config.ScheduledTaskHolder;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = {"ratelimit.limit=2", "ratelimit.window=30s", "ratelimit.eviction-interval=7s"})
@AutoConfigureMockMvc
class RateLimitPropertiesOverrideTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private RateLimitProperties properties;

    @Autowired
    private ScheduledTaskHolder scheduledTaskHolder;

    @Test
    void evictionIsScheduledAtConfiguredInterval() {
        assertThat(properties.evictionInterval()).isEqualTo(Duration.ofSeconds(7));
        assertThat(scheduledTaskHolder.getScheduledTasks())
                .map(ScheduledTask::getTask)
                .filteredOn(FixedDelayTask.class::isInstance)
                .map(task -> ((FixedDelayTask) task).getIntervalDuration())
                .contains(Duration.ofSeconds(7));
    }

    @Test
    void limitAndWindowAreConfigurableWithoutCodeChanges() throws Exception {
        assertThat(properties.limit()).isEqualTo(2);
        assertThat(properties.window()).isEqualTo(Duration.ofSeconds(30));

        mockMvc.perform(get("/api/quotes/random").header("X-API-Key", "override-key"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-RateLimit-Limit", "2"))
                .andExpect(header().string("X-RateLimit-Remaining", "1"));
        mockMvc.perform(get("/api/quotes/random").header("X-API-Key", "override-key"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/quotes/random").header("X-API-Key", "override-key"))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"));
    }
}
