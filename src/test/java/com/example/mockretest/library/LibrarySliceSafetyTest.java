package com.example.mockretest.library;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Proves the library's slice-included beans ({@link LibraryExceptionHandler} and any WebMvcConfigurer, filter,
 * interceptor, converter or JSON component) need no services, repositories, properties or configuration beans: a
 * {@code @WebMvcTest} for an unrelated controller, with no {@code @Import} and no mocks, must start and serve requests.
 * Every other feature's {@code @WebMvcTest} in the merged app loads these beans the same way.
 */
@WebMvcTest(LibrarySliceSafetyTest.SliceProbeController.class)
class LibrarySliceSafetyTest {

    /**
     * Registers only the probe controller. Nested classes of tests are excluded from component scanning (so the probe
     * never leaks into full-context tests), hence the explicit bean; nothing from the library is imported.
     */
    @TestConfiguration(proxyBeanMethods = false)
    static class ProbeOnlyConfig {
        @Bean
        SliceProbeController sliceProbeController() {
            return new SliceProbeController();
        }
    }

    @RestController
    static class SliceProbeController {

        @GetMapping("/library-slice-probe")
        String probe() {
            return "ok";
        }

        @GetMapping("/library-slice-probe/missing")
        String missing() {
            throw new LibraryBookNotFoundException(42L);
        }
    }

    @Autowired
    private MockMvc mockMvc;

    @Test
    void sliceWithoutImportsStartsAndServesRequest() throws Exception {
        mockMvc.perform(get("/library-slice-probe"))
                .andExpect(status().isOk())
                .andExpect(content().string("ok"));
    }

    @Test
    void libraryExceptionHandlerWorksInSliceWithoutDependencies() throws Exception {
        mockMvc.perform(get("/library-slice-probe/missing"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.instance").value("/library-slice-probe/missing"));
    }
}
