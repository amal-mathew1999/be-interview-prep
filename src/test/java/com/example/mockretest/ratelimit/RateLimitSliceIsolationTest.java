package com.example.mockretest.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Simulates another feature's {@code @WebMvcTest} in the merged app: with no {@code @Import}, the slice must start and
 * serve requests, i.e. the rate-limit feature must not contribute slice-included beans (interceptors, {@code
 * WebMvcConfigurer}s, ...) that depend on beans the slice excludes.
 */
@WebMvcTest(RateLimitSliceProbeController.class)
@ActiveProfiles(RateLimitSliceProbeController.PROFILE)
class RateLimitSliceIsolationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ApplicationContext context;

    @Test
    void unrelatedControllerSliceStartsAndServesRequestsWithoutRateLimitBeans() throws Exception {
        mockMvc.perform(get(RateLimitSliceProbeController.PATH))
                .andExpect(status().isOk())
                .andExpect(content().string("ok"))
                .andExpect(header().doesNotExist(RateLimitInterceptor.LIMIT_HEADER));

        assertThat(context.getBeanNamesForType(RateLimitInterceptor.class)).isEmpty();
        assertThat(context.getBeanNamesForType(RateLimitConfig.class)).isEmpty();
        assertThat(context.getBeanNamesForType(RateLimitService.class)).isEmpty();
    }
}
