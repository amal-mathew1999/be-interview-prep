package com.example.mockretest.files;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.endsWith;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/** Web-layer slice: request mapping, response shape and problem+json error mapping, with the service mocked. */
@WebMvcTest(FilesController.class)
@Import(FilesConfig.class)
class FilesControllerTest {

    private static final byte[] PNG_BYTES = {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A};

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private FilesService service;

    private static MockMultipartFile pngPart() {
        return new MockMultipartFile("file", "photo.png", "image/png", PNG_BYTES);
    }

    private static StoredFile record(String name, String contentType, long size) {
        return new StoredFile(UUID.randomUUID(), name, contentType, size, Instant.parse("2026-01-02T03:04:05Z"));
    }

    @Test
    void uploadReturns201WithLocationAndRecord() throws Exception {
        StoredFile stored = record("photo.png", "image/png", PNG_BYTES.length);
        when(service.upload(any())).thenReturn(stored);

        mockMvc.perform(multipart("/api/files").file(pngPart()))
                .andExpect(status().isCreated())
                .andExpect(header().string(HttpHeaders.LOCATION, endsWith("/api/files/" + stored.getId())))
                .andExpect(jsonPath("$.id").value(stored.getId().toString()))
                .andExpect(jsonPath("$.originalName").value("photo.png"))
                .andExpect(jsonPath("$.contentType").value("image/png"))
                .andExpect(jsonPath("$.size").value(PNG_BYTES.length))
                .andExpect(jsonPath("$.uploadedAt").value("2026-01-02T03:04:05Z"));
    }

    @Test
    void uploadMapsUnsupportedTypeTo415Problem() throws Exception {
        when(service.upload(any())).thenThrow(new UnsupportedFileTypeException());

        mockMvc.perform(multipart("/api/files").file(pngPart()))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").isNotEmpty())
                .andExpect(jsonPath("$.title").value("Unsupported file type"))
                .andExpect(jsonPath("$.status").value(415))
                .andExpect(jsonPath("$.detail", containsString("JPEG, PNG, PDF")))
                .andExpect(jsonPath("$.instance").value("/api/files"));
    }

    @Test
    void uploadMapsEmptyFileTo400Problem() throws Exception {
        when(service.upload(any())).thenThrow(new EmptyFileException());

        mockMvc.perform(multipart("/api/files").file(pngPart()))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.detail", containsString("empty")));
    }

    @Test
    void uploadMapsTooLargeTo413Problem() throws Exception {
        when(service.upload(any())).thenThrow(new FileTooLargeException("5MB"));

        mockMvc.perform(multipart("/api/files").file(pngPart()))
                .andExpect(status().isPayloadTooLarge())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(413))
                .andExpect(jsonPath("$.title").value("File too large"))
                .andExpect(jsonPath("$.detail", containsString("5MB")))
                .andExpect(jsonPath("$.instance").value("/api/files"));
    }

    @Test
    void uploadWithoutFilePartReturns400Problem() throws Exception {
        mockMvc.perform(multipart("/api/files"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").isNotEmpty())
                .andExpect(jsonPath("$.instance").value("/api/files"));
    }

    @Test
    void unexpectedErrorReturnsGeneric500ProblemWithoutInternalMessage() throws Exception {
        when(service.upload(any())).thenThrow(new IllegalStateException("secret internal path C:/data"));

        mockMvc.perform(multipart("/api/files").file(pngPart()))
                .andExpect(status().isInternalServerError())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(500))
                .andExpect(jsonPath("$.detail", not(containsString("secret"))));
    }

    @Test
    void listReturnsRecords() throws Exception {
        when(service.list()).thenReturn(List.of(record("a.pdf", "application/pdf", 20)));

        mockMvc.perform(get("/api/files"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].originalName").value("a.pdf"))
                .andExpect(jsonPath("$[0].contentType").value("application/pdf"))
                .andExpect(jsonPath("$[0].size").value(20))
                .andExpect(jsonPath("$[0].uploadedAt").isNotEmpty());
    }

    @Test
    void downloadSendsBytesWithTypeAndEncodedAttachmentName() throws Exception {
        String name = "ré\"sumé;x.jpg";
        byte[] bytes = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 0x01};
        StoredFile stored = record(name, "image/jpeg", bytes.length);
        when(service.download(stored.getId().toString()))
                .thenReturn(new FileDownload(stored, new ByteArrayResource(bytes)));

        MvcResult result = mockMvc.perform(get("/api/files/{id}", stored.getId()))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.IMAGE_JPEG))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, containsString("filename*=UTF-8''")))
                .andExpect(content().bytes(bytes))
                .andReturn();

        String headerValue = result.getResponse().getHeader(HttpHeaders.CONTENT_DISPOSITION);
        assertThat(StandardCharsets.US_ASCII.newEncoder().canEncode(headerValue))
                .isTrue();
        ContentDisposition disposition = ContentDisposition.parse(headerValue);
        assertThat(disposition.isAttachment()).isTrue();
        assertThat(disposition.getFilename()).isEqualTo(name);
    }

    @Test
    void downloadOfUnknownIdReturns404Problem() throws Exception {
        String id = UUID.randomUUID().toString();
        when(service.download(id)).thenThrow(new StoredFileNotFoundException(id));

        mockMvc.perform(get("/api/files/{id}", id))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").isNotEmpty())
                .andExpect(jsonPath("$.title").value("File not found"))
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.detail", containsString(id)))
                .andExpect(jsonPath("$.instance").value("/api/files/" + id));
    }

    @Test
    void deleteReturns204() throws Exception {
        String id = UUID.randomUUID().toString();

        mockMvc.perform(delete("/api/files/{id}", id)).andExpect(status().isNoContent());

        verify(service).delete(id);
    }

    @Test
    void deleteOfUnknownIdReturns404Problem() throws Exception {
        String id = UUID.randomUUID().toString();
        doThrow(new StoredFileNotFoundException(id)).when(service).delete(id);

        mockMvc.perform(delete("/api/files/{id}", id))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.instance").value("/api/files/" + id));
    }
}
