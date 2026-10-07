package com.example.mockretest.files;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Another feature's {@code @WebMvcTest} slice (no files config imported) still picks up every
 * {@code @RestControllerAdvice}, including {@link FilesExceptionHandler}. It must start without any files-feature
 * bean that is outside the MVC slice.
 */
@WebMvcTest(FilesSliceIsolationTest.ProbeController.class)
@Import(FilesSliceIsolationTest.ProbeController.class) // only the probe itself; nothing from the files config
class FilesSliceIsolationTest {

    @RestController
    static class ProbeController {

        @GetMapping("/files-slice-probe")
        String probe() {
            return "ok";
        }
    }

    @Autowired
    private MockMvc mockMvc;

    @Test
    void foreignWebMvcSliceStartsAndServesRequestsWithoutFilesConfig() throws Exception {
        mockMvc.perform(get("/files-slice-probe"))
                .andExpect(status().isOk())
                .andExpect(content().string("ok"));
    }
}
