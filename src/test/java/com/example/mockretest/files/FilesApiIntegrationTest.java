package com.example.mockretest.files;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Full-context tests against a real embedded server: real storage directory, database and, for the container-level
 * size limit, real multipart parsing over HTTP. Named {@code *Test} so the default Surefire configuration runs it.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
class FilesApiIntegrationTest {

    private static final int FIVE_MB = 5 * 1024 * 1024;
    private static final byte[] PNG_MAGIC = {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A};
    private static final byte[] JPEG_MAGIC = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0};
    private static final byte[] PDF_MAGIC = "%PDF-1.7\n".getBytes(StandardCharsets.US_ASCII);

    @TempDir
    static Path tempRoot;

    @DynamicPropertySource
    static void storageProperties(DynamicPropertyRegistry registry) {
        registry.add("files.storage-dir", () -> storageDir().toString());
    }

    private static Path storageDir() {
        // Nested so that "../../x" from the storage dir still lands inside tempRoot, where the test can observe it.
        return tempRoot.resolve("a").resolve("b").resolve("storage");
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private FilesService filesService;

    @LocalServerPort
    private int port;

    private static byte[] withMagic(byte[] magic, int totalSize) {
        byte[] bytes = Arrays.copyOf(magic, totalSize);
        for (int i = magic.length; i < totalSize; i++) {
            bytes[i] = (byte) (i % 251);
        }
        return bytes;
    }

    private static MockMultipartFile filePart(String name, String clientType, byte[] bytes) {
        return new MockMultipartFile("file", name, clientType, bytes);
    }

    private String upload(MockMultipartFile file) throws Exception {
        MvcResult result = mockMvc.perform(multipart("/api/files").file(file))
                .andExpect(status().isCreated())
                .andReturn();
        return JsonPath.read(result.getResponse().getContentAsString(), "$.id");
    }

    private static List<Path> storedFiles() throws IOException {
        if (!Files.exists(storageDir())) {
            return List.of();
        }
        try (Stream<Path> files = Files.list(storageDir())) {
            return files.toList();
        }
    }

    @Test
    void uploadsPngAndReturns201WithLocationAndRecord() throws Exception {
        byte[] bytes = withMagic(PNG_MAGIC, 64);
        mockMvc.perform(multipart("/api/files").file(filePart("photo.png", "image/png", bytes)))
                .andExpect(status().isCreated())
                .andExpect(header().string(HttpHeaders.LOCATION, containsString("/api/files/")))
                .andExpect(jsonPath("$.id").isNotEmpty())
                .andExpect(jsonPath("$.originalName").value("photo.png"))
                .andExpect(jsonPath("$.contentType").value("image/png"))
                .andExpect(jsonPath("$.size").value(64))
                .andExpect(jsonPath("$.uploadedAt").isNotEmpty());
    }

    @Test
    void uploadsJpegDetectedFromContentNotClientType() throws Exception {
        byte[] bytes = withMagic(JPEG_MAGIC, 32);
        mockMvc.perform(multipart("/api/files")
                        .file(filePart("picture.bin", MediaType.APPLICATION_OCTET_STREAM_VALUE, bytes)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.contentType").value("image/jpeg"));
    }

    @Test
    void uploadsPdf() throws Exception {
        byte[] bytes = withMagic(PDF_MAGIC, 128);
        mockMvc.perform(multipart("/api/files").file(filePart("doc.pdf", "application/pdf", bytes)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.contentType").value("application/pdf"))
                .andExpect(jsonPath("$.size").value(128));
    }

    @Test
    void rejectsRenamedExecutableWith415Problem() throws Exception {
        byte[] exe = withMagic(new byte[] {'M', 'Z', (byte) 0x90, 0x00}, 64);
        mockMvc.perform(multipart("/api/files").file(filePart("photo.png", "image/png", exe)))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(415))
                .andExpect(jsonPath("$.title").isNotEmpty())
                .andExpect(jsonPath("$.type").isNotEmpty())
                .andExpect(jsonPath("$.instance").value("/api/files"))
                .andExpect(jsonPath("$.detail", containsString("JPEG, PNG, PDF")));
    }

    @Test
    void rejectsEmptyFileWith400Problem() throws Exception {
        mockMvc.perform(multipart("/api/files").file(filePart("empty.png", "image/png", new byte[0])))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.detail", containsString("empty")));
    }

    @Test
    void rejectsMissingFilePartWith400Problem() throws Exception {
        mockMvc.perform(multipart("/api/files"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").isNotEmpty())
                .andExpect(jsonPath("$.instance").value("/api/files"));
    }

    @Test
    void acceptsFileOfExactlyFiveMegabytes() throws Exception {
        byte[] bytes = withMagic(PNG_MAGIC, FIVE_MB);
        mockMvc.perform(multipart("/api/files").file(filePart("big.png", "image/png", bytes)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.size").value(FIVE_MB));
    }

    @Test
    void rejectsFileLargerThanFiveMegabytesWith413Problem() throws Exception {
        byte[] bytes = withMagic(PNG_MAGIC, FIVE_MB + 1);
        mockMvc.perform(multipart("/api/files").file(filePart("huge.png", "image/png", bytes)))
                .andExpect(status().isPayloadTooLarge())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(413))
                .andExpect(jsonPath("$.type").isNotEmpty())
                .andExpect(jsonPath("$.title").isNotEmpty())
                .andExpect(jsonPath("$.instance").value("/api/files"))
                .andExpect(jsonPath("$.detail", containsString("5MB")));
    }

    @Test
    void storesTraversalFilenameSafelyInsideStorageDir() throws Exception {
        for (String evil : List.of(
                "../../passwd", "..\\..\\passwd", "../../../passwd", "/etc/passwd", "C:\\passwd", "pass\u0000wd")) {
            Set<Path> before = treeOf(tempRoot);

            String id = upload(filePart(evil, "image/png", withMagic(PNG_MAGIC, 16)));

            Path stored = storageDir().resolve(id);
            assertThat(stored).exists();
            assertThat(stored.toRealPath().getParent()).isEqualTo(storageDir().toRealPath());
            Set<Path> created = new HashSet<>(treeOf(tempRoot));
            created.removeAll(before);
            assertThat(created)
                    .as("only new file for %s", evil)
                    .containsExactly(stored.toAbsolutePath().normalize());
            assertThat(tempRoot.getParent().resolve("passwd")).doesNotExist();

            mockMvc.perform(get("/api/files"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[?(@.id == '" + id + "')].originalName", hasItem("passwd")));
        }
    }

    @Test
    void acceptsFileOfExactlyFiveMegabytesOverRealHttp() throws Exception {
        // Real container multipart parsing: fails if the app-level spring.servlet.multipart.* limits drop below 5MB.
        HttpResponse<String> response = postOverHttp(withMagic(PNG_MAGIC, FIVE_MB));

        assertThat(response.statusCode()).isEqualTo(201);
        assertThat(response.headers().firstValue(HttpHeaders.LOCATION))
                .hasValueSatisfying(location -> assertThat(location).contains("/api/files/"));
        assertThat((Integer) JsonPath.read(response.body(), "$.size")).isEqualTo(FIVE_MB);
    }

    @Test
    void rejectsFileOfFiveMegabytesPlusOneByteOverRealHttpWithAppLevel413() throws Exception {
        HttpResponse<String> response = postOverHttp(withMagic(PNG_MAGIC, FIVE_MB + 1));

        assertTooLargeProblem(response);
    }

    @Test
    void rejectsBodyAboveContainerMultipartLimitWith413ProblemOverRealHttp() throws Exception {
        // 8MB exceeds spring.servlet.multipart.max-file-size (6MB) and max-request-size (7MB): the servlet container
        // rejects it while parsing the multipart body, before the application-level size check can run.
        HttpResponse<String> response = postOverHttp(withMagic(PNG_MAGIC, 8 * 1024 * 1024));

        assertTooLargeProblem(response);
    }

    @Test
    void uploadCopiesBytesWithoutAnOpenTransaction() {
        List<Boolean> transactionActiveDuringRead = new ArrayList<>();
        MockMultipartFile file = new MockMultipartFile("file", "tx.png", "image/png", withMagic(PNG_MAGIC, 32)) {
            @Override
            public InputStream getInputStream() throws IOException {
                transactionActiveDuringRead.add(TransactionSynchronizationManager.isActualTransactionActive());
                return super.getInputStream();
            }
        };

        FilesStoredFile stored = filesService.upload(file);

        assertThat(transactionActiveDuringRead).isNotEmpty().containsOnly(false);
        assertThat(storageDir().resolve(stored.getId().toString())).exists();
    }

    private HttpResponse<String> postOverHttp(byte[] bytes) throws IOException, InterruptedException {
        String boundary = "files-boundary-" + UUID.randomUUID();
        ByteArrayOutputStream body = new ByteArrayOutputStream();
        body.write(("--" + boundary + "\r\n"
                        + "Content-Disposition: form-data; name=\"file\"; filename=\"upload.png\"\r\n"
                        + "Content-Type: image/png\r\n\r\n")
                .getBytes(StandardCharsets.US_ASCII));
        body.write(bytes);
        body.write(("\r\n--" + boundary + "--\r\n").getBytes(StandardCharsets.US_ASCII));

        HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/files"))
                .header(HttpHeaders.CONTENT_TYPE, "multipart/form-data; boundary=" + boundary)
                .POST(HttpRequest.BodyPublishers.ofByteArray(body.toByteArray()))
                .build();
        return HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }

    private static void assertTooLargeProblem(HttpResponse<String> response) {
        assertThat(response.statusCode()).isEqualTo(413);
        assertThat(MediaType.parseMediaType(
                        response.headers().firstValue(HttpHeaders.CONTENT_TYPE).orElseThrow()))
                .satisfies(type -> assertThat(type.isCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                        .isTrue());
        Map<String, Object> problem = JsonPath.parse(response.body()).read("$");
        assertThat(problem)
                .containsEntry("status", 413)
                .containsEntry("title", "File too large")
                .containsEntry("instance", "/api/files")
                .containsKeys("type", "detail");
        assertThat((String) problem.get("detail")).contains("maximum allowed size of 5MB");
    }

    @Test
    void listsAllRecords() throws Exception {
        String id = upload(filePart("listed.pdf", "application/pdf", withMagic(PDF_MAGIC, 20)));

        mockMvc.perform(get("/api/files"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == '" + id + "')].originalName", hasItem("listed.pdf")))
                .andExpect(jsonPath("$[?(@.id == '" + id + "')].contentType", hasItem("application/pdf")))
                .andExpect(jsonPath("$[?(@.id == '" + id + "')].size", hasItem(20)))
                .andExpect(jsonPath("$[?(@.id == '" + id + "')].uploadedAt").isNotEmpty());
    }

    @Test
    void downloadsBytesWithDetectedTypeAndSafeAttachmentName() throws Exception {
        byte[] bytes = withMagic(JPEG_MAGIC, 40);
        String name = "ré\"sumé;x.jpg";
        String id = upload(filePart(name, "text/plain", bytes));

        MvcResult result = mockMvc.perform(get("/api/files/{id}", id))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.IMAGE_JPEG))
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, startsWith("attachment")))
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, containsString("filename*=UTF-8''")))
                .andExpect(content().bytes(bytes))
                .andReturn();

        ContentDisposition disposition =
                ContentDisposition.parse(result.getResponse().getHeader(HttpHeaders.CONTENT_DISPOSITION));
        assertThat(disposition.isAttachment()).isTrue();
        assertThat(disposition.getFilename()).isEqualTo(name);
    }

    @Test
    void returns404ProblemWhenDownloadingUnknownId() throws Exception {
        mockMvc.perform(get("/api/files/{id}", UUID.randomUUID()))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.type").isNotEmpty())
                .andExpect(jsonPath("$.title").isNotEmpty())
                .andExpect(jsonPath("$.detail").isNotEmpty())
                .andExpect(jsonPath("$.instance", startsWith("/api/files/")));
    }

    @Test
    void returns404WhenIdIsNotAUuid() throws Exception {
        mockMvc.perform(get("/api/files/{id}", "..%2F..%2Fetc%2Fpasswd"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
    }

    @Test
    void deleteRemovesBytesFromDiskAndRecord() throws Exception {
        String id = upload(filePart("gone.png", "image/png", withMagic(PNG_MAGIC, 16)));
        assertThat(storageDir().resolve(id)).exists();

        mockMvc.perform(delete("/api/files/{id}", id)).andExpect(status().isNoContent());

        assertThat(storageDir().resolve(id)).doesNotExist();
        mockMvc.perform(get("/api/files/{id}", id)).andExpect(status().isNotFound());
        mockMvc.perform(get("/api/files"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == '" + id + "')]").isEmpty());
    }

    @Test
    void returns404ProblemWhenDeletingUnknownId() throws Exception {
        mockMvc.perform(delete("/api/files/{id}", UUID.randomUUID()))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.instance", startsWith("/api/files/")));
    }

    private static Set<Path> treeOf(Path root) throws IOException {
        try (Stream<Path> paths = Files.walk(root)) {
            return paths.filter(Files::isRegularFile)
                    .map(p -> p.toAbsolutePath().normalize())
                    .collect(Collectors.toSet());
        }
    }
}
