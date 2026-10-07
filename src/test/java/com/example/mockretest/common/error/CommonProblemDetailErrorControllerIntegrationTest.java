package com.example.mockretest.common.error;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.servlet.DispatcherType;
import jakarta.servlet.Filter;
import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.WriteListener;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpServletResponseWrapper;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.net.Socket;
import java.net.SocketException;
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.servlet.autoconfigure.MultipartProperties;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.core.Ordered;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.util.unit.DataSize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.client.RestClient;
import org.springframework.web.multipart.MultipartFile;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import({
    CommonProblemDetailErrorControllerIntegrationTest.ErrorProbeController.class,
    CommonProblemDetailErrorControllerIntegrationTest.ErrorProbeHandler.class,
    CommonProblemDetailErrorControllerIntegrationTest.ErrorDispatchProbeConfig.class
})
@ExtendWith(OutputCaptureExtension.class)
class CommonProblemDetailErrorControllerIntegrationTest {

    private static final String SECRET = "SecretInternalDetail-42";
    private static final String CRLF = "\r\n";
    private static final String PARTIAL_BODY = "{\"items\":[\"partial-stream-content\"";
    private static final String STREAM_FIRST = "first-streamed-part;";
    private static final String STREAM_SECOND = "second-streamed-part";
    private static final ParameterizedTypeReference<Map<String, Object>> JSON_MAP =
            new ParameterizedTypeReference<>() {};
    private static final JsonMapper JSON = new JsonMapper();
    private static final TypeReference<Map<String, Object>> JSON_MAP_TYPE = new TypeReference<>() {};

    @LocalServerPort
    private int port;

    @Autowired
    private ErrorDispatchHeaderProbe headerProbe;

    @Autowired
    private ErrorDispatchOutputProbe outputProbe;

    @Autowired
    private MultipartProperties multipartProperties;

    private RestClient client;

    @BeforeEach
    void setUp() {
        client = RestClient.builder()
                .baseUrl("http://localhost:" + port)
                .requestFactory(new JdkClientHttpRequestFactory(HttpClient.newHttpClient()))
                .defaultStatusHandler(status -> true, (request, response) -> {})
                .build();
    }

    @Test
    void returns405ProblemWithAllowHeaderForUnsupportedMethod() {
        ResponseEntity<Map<String, Object>> response =
                client.delete().uri("/test-errors/ok").retrieve().toEntity(JSON_MAP);

        assertProblem(response, HttpStatus.METHOD_NOT_ALLOWED, "/test-errors/ok");
        assertThat(response.getHeaders().getAllow()).contains(HttpMethod.GET);
        assertThat(response.getHeaders().get(HttpHeaders.ALLOW)).hasSize(1);
    }

    @Test
    void names405DetailAfterOriginalRequestMethodNotErrorDispatchMethod() {
        ResponseEntity<Map<String, Object>> response =
                client.delete().uri("/test-errors/get-post").retrieve().toEntity(JSON_MAP);

        assertProblem(response, HttpStatus.METHOD_NOT_ALLOWED, "/test-errors/get-post");
        assertThat((String) response.getBody().get("detail"))
                .contains("DELETE")
                .doesNotContain("GET is not supported")
                .doesNotContain("POST is not supported");
        assertThat(response.getHeaders().getAllow()).containsExactlyInAnyOrder(HttpMethod.GET, HttpMethod.POST);
    }

    @Test
    void names405DetailAfterOriginalMethodOverRawHttp() throws Exception {
        RawResponse raw = rawRequest("DELETE /test-errors/get-post HTTP/1.1", new byte[0], null);

        assertLengthDelimited(raw);
        ResponseEntity<Map<String, Object>> response = raw.toEntity();
        assertProblem(response, HttpStatus.METHOD_NOT_ALLOWED, "/test-errors/get-post");
        assertThat((String) response.getBody().get("detail")).contains("DELETE").doesNotContain("GET is not supported");
        assertThat(response.getHeaders().getAllow()).containsExactlyInAnyOrder(HttpMethod.GET, HttpMethod.POST);
    }

    @Test
    void names404DetailAfterOriginalRequestMethod() {
        ResponseEntity<Map<String, Object>> response =
                client.delete().uri("/api/does-not-exist").retrieve().toEntity(JSON_MAP);

        assertProblem(response, HttpStatus.NOT_FOUND, "/api/does-not-exist");
        assertThat(response.getBody()).containsEntry("detail", "No resource found for DELETE /api/does-not-exist.");
    }

    @Test
    void names404DetailAfterOriginalMethodForPlainSendError() {
        ResponseEntity<Map<String, Object>> response =
                client.delete().uri("/test-errors/send-error-404").retrieve().toEntity(JSON_MAP);

        assertProblem(response, HttpStatus.NOT_FOUND, "/test-errors/send-error-404");
        assertThat(response.getBody())
                .containsEntry("detail", "No resource found for DELETE /test-errors/send-error-404.");
    }

    @Test
    void logsOriginalMethodForUncaughtExceptionOnPost(CapturedOutput output) {
        ResponseEntity<Map<String, Object>> response =
                client.post().uri("/test-errors/boom").retrieve().toEntity(JSON_MAP);

        assertProblem(response, HttpStatus.INTERNAL_SERVER_ERROR, "/test-errors/boom");
        assertThat(output.getAll())
                .contains("Request POST /test-errors/boom failed with status 500")
                .doesNotContain("Request GET /test-errors/boom failed");
    }

    @Test
    void logsOriginalMethodForServerSendErrorOnPut(CapturedOutput output) {
        ResponseEntity<Map<String, Object>> response =
                client.put().uri("/test-errors/send-error-503").retrieve().toEntity(JSON_MAP);

        assertProblem(response, HttpStatus.SERVICE_UNAVAILABLE, "/test-errors/send-error-503");
        assertThat(output.getAll())
                .contains("Request PUT /test-errors/send-error-503 failed with status 503")
                .doesNotContain("Request GET /test-errors/send-error-503 failed");
    }

    @Test
    void leavesSuccessfulResponsesUnchangedOverRawHttp() throws IOException {
        String raw = rawGet("/test-errors/ok");

        assertThat(raw).startsWith("HTTP/1.1 200").endsWith(CRLF + CRLF + "ok");
        assertThat(raw.toLowerCase(Locale.ROOT)).contains("content-length: 2").doesNotContain("problem+json");
    }

    @Test
    void keepsStreamedSuccessfulResponsesChunked() throws IOException {
        String raw = rawGet("/test-errors/stream");

        assertThat(raw).startsWith("HTTP/1.1 200").contains(STREAM_FIRST).contains(STREAM_SECOND);
        assertThat(raw.toLowerCase(Locale.ROOT))
                .contains("transfer-encoding: chunked")
                .doesNotContain("content-length:");
        assertThat(raw).endsWith("0" + CRLF + CRLF);
    }

    @Test
    void returns405ProblemForUnsupportedMethodOnActuatorHealth() {
        ResponseEntity<Map<String, Object>> response =
                client.delete().uri("/actuator/health").retrieve().toEntity(JSON_MAP);

        assertProblem(response, HttpStatus.METHOD_NOT_ALLOWED, "/actuator/health");
        assertThat(response.getHeaders().getAllow()).contains(HttpMethod.GET);
    }

    @Test
    void returns404ProblemWithOriginalPathAsInstanceForUnmappedPath() {
        ResponseEntity<Map<String, Object>> response =
                client.get().uri("/api/does-not-exist").retrieve().toEntity(JSON_MAP);

        assertProblem(response, HttpStatus.NOT_FOUND, "/api/does-not-exist");
        assertThat(response.getBody().get("instance")).isNotEqualTo("/error");
    }

    @Test
    void returns404ProblemEvenWhenClientAcceptsHtml() {
        ResponseEntity<Map<String, Object>> response = client.get()
                .uri("/api/does-not-exist")
                .accept(MediaType.TEXT_HTML)
                .retrieve()
                .toEntity(JSON_MAP);

        assertProblem(response, HttpStatus.NOT_FOUND, "/api/does-not-exist");
    }

    @Test
    void returns413ProblemForMultipartExceedingContainerLimit() throws Exception {
        RawResponse raw = rawMultipartUpload("/test-errors/upload", oversizedUploadBytes());

        assertLengthDelimited(raw);
        ResponseEntity<Map<String, Object>> response = raw.toEntity();
        assertProblem(response, HttpStatus.CONTENT_TOO_LARGE, "/test-errors/upload");
        assertThat((String) response.getBody().get("detail")).containsIgnoringCase("upload size");
        assertThat(headerProbe.headerNames.get())
                .isNotNull()
                .isNotEmpty()
                .noneMatch(name -> name.equalsIgnoreCase(HttpHeaders.CONTENT_TYPE));
    }

    @Test
    void sendsErrorProblemBodiesWithContentLengthInsteadOfChunked() throws Exception {
        RawResponse notFound = rawRequest("GET /api/does-not-exist HTTP/1.1", new byte[0], null);
        RawResponse serverError = rawRequest("GET /test-errors/boom HTTP/1.1", new byte[0], null);

        assertLengthDelimited(notFound);
        assertProblem(notFound.toEntity(), HttpStatus.NOT_FOUND, "/api/does-not-exist");
        assertLengthDelimited(serverError);
        assertProblem(serverError.toEntity(), HttpStatus.INTERNAL_SERVER_ERROR, "/test-errors/boom");
    }

    @Test
    void returnsGenericContentTooLargeDetailForNonMultipartSendError413() {
        ResponseEntity<Map<String, Object>> response =
                client.get().uri("/test-errors/send-error-413").retrieve().toEntity(JSON_MAP);

        assertProblem(response, HttpStatus.CONTENT_TOO_LARGE, "/test-errors/send-error-413");
        assertThat(response.getBody())
                .containsEntry("detail", CommonProblemDetailErrorController.CONTENT_TOO_LARGE_DETAIL);
        assertNoInternalsLeaked(response);
    }

    @Test
    void preservesNonStandardClientStatusFromSendError() {
        ResponseEntity<Map<String, Object>> response =
                client.get().uri("/test-errors/send-error-499").retrieve().toEntity(JSON_MAP);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatusCode.valueOf(499));
        assertThat(response.getHeaders().getContentType()).isNotNull();
        assertThat(response.getHeaders().getContentType().isCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .isTrue();
        assertThat(response.getBody())
                .containsEntry("type", "urn:problem-type:common:499")
                .containsEntry("title", "Client Error")
                .containsEntry("status", 499)
                .containsEntry("instance", "/test-errors/send-error-499");
        assertThat((String) response.getBody().get("detail"))
                .isNotBlank()
                .contains("499")
                .isNotEqualTo(CommonProblemDetailErrorController.GENERIC_SERVER_DETAIL);
        assertNoInternalsLeaked(response);
    }

    @Test
    void treatsNonStandardStatusAbove5xxAsLoggedServerError(CapturedOutput output) {
        ResponseEntity<Map<String, Object>> response =
                client.get().uri("/test-errors/send-error-600").retrieve().toEntity(JSON_MAP);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatusCode.valueOf(600));
        assertThat(response.getBody())
                .containsEntry("type", "urn:problem-type:common:600")
                .containsEntry("title", CommonProblemDetailErrorController.SERVER_ERROR_TITLE)
                .containsEntry("detail", CommonProblemDetailErrorController.GENERIC_SERVER_DETAIL);
        assertNoInternalsLeaked(response);
        assertThat(output.getAll()).contains("Request GET /test-errors/send-error-600 failed with status 600");
    }

    @Test
    void logsUncaughtExceptionStackTraceOnlyOnce(CapturedOutput output) {
        ResponseEntity<Map<String, Object>> response =
                client.get().uri("/test-errors/boom").retrieve().toEntity(JSON_MAP);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(output.getAll()).contains("Request GET /test-errors/boom failed with status 500");
        String stackTraceHeader = "java.lang.IllegalStateException: " + SECRET;
        long stackTraces = output.getAll()
                .lines()
                .filter(line -> line.strip().startsWith(stackTraceHeader))
                .count();
        assertThat(stackTraces).isEqualTo(1);
    }

    @Test
    void returns500ProblemWithGenericDetailForUncaughtException() {
        ResponseEntity<Map<String, Object>> response =
                client.get().uri("/test-errors/boom").retrieve().toEntity(JSON_MAP);

        assertProblem(response, HttpStatus.INTERNAL_SERVER_ERROR, "/test-errors/boom");
        assertNoInternalsLeaked(response);
    }

    @Test
    void returnsOriginalStatusWithGenericDetailForServerSendError() {
        ResponseEntity<Map<String, Object>> response =
                client.get().uri("/test-errors/send-error-503").retrieve().toEntity(JSON_MAP);

        assertProblem(response, HttpStatus.SERVICE_UNAVAILABLE, "/test-errors/send-error-503");
        assertNoInternalsLeaked(response);
    }

    @Test
    void returnsOriginalStatusForClientSendError() {
        ResponseEntity<Map<String, Object>> response =
                client.get().uri("/test-errors/send-error-409").retrieve().toEntity(JSON_MAP);

        assertProblem(response, HttpStatus.CONFLICT, "/test-errors/send-error-409");
        assertNoInternalsLeaked(response);
    }

    @Test
    void leavesFeatureHandledErrorsUnchanged() {
        ResponseEntity<Map<String, Object>> response =
                client.get().uri("/test-errors/handled").retrieve().toEntity(JSON_MAP);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_CONTENT);
        assertThat(response.getBody())
                .containsEntry("type", "urn:problem-type:test:handled")
                .containsEntry("title", "Handled by feature")
                .containsEntry("detail", "feature detail")
                .containsEntry("instance", "/test-errors/handled");
    }

    @Test
    void doesNotWriteProblemBodyWhenResponseAlreadyCommitted() throws IOException {
        String raw = rawGet("/test-errors/partial");

        assertThat(raw).startsWith("HTTP/1.1 200").contains(PARTIAL_BODY);
        assertThat(raw)
                .doesNotContain("urn:problem-type")
                .doesNotContain(CommonProblemDetailErrorController.GENERIC_SERVER_DETAIL)
                .doesNotContain(SECRET);
        // The container may discard error-include output on the wire, so also check nothing was written at all.
        assertThat(outputProbe.bytesWrittenByUri).containsEntry("/test-errors/partial", 0L);
    }

    @Test
    void writesProblemBodyDuringErrorDispatchWhenResponseNotCommitted() {
        client.get().uri("/test-errors/boom").retrieve().toEntity(JSON_MAP);

        assertThat(outputProbe.bytesWrittenByUri.get("/test-errors/boom")).isPositive();
    }

    /** Reads everything the server writes, tolerating the aborted chunked stream of a failed committed response. */
    private String rawGet(String path) throws IOException {
        try (Socket socket = new Socket("localhost", port)) {
            socket.setSoTimeout(5_000);
            OutputStream out = socket.getOutputStream();
            out.write(
                    ("GET " + path + " HTTP/1.1" + CRLF + "Host: localhost" + CRLF + "Connection: close" + CRLF + CRLF)
                            .getBytes(StandardCharsets.US_ASCII));
            out.flush();
            ByteArrayOutputStream received = new ByteArrayOutputStream();
            InputStream in = socket.getInputStream();
            byte[] buffer = new byte[4096];
            try {
                for (int read = in.read(buffer); read != -1; read = in.read(buffer)) {
                    received.write(buffer, 0, read);
                }
            } catch (SocketException ex) {
                // Connection reset after the partial body is acceptable; keep what was received.
            }
            return received.toString(StandardCharsets.UTF_8);
        }
    }

    /**
     * Uploads a multipart body over a raw socket. The body is written on a separate thread while the response is read,
     * so a server that answers early and then closes the connection (leaving part of the upload unread) cannot make the
     * client fail on a broken pipe before it has read the response.
     */
    private RawResponse rawMultipartUpload(String path, int fileBytes) throws Exception {
        String boundary = "common-error-boundary-" + System.nanoTime();
        ByteArrayOutputStream body = new ByteArrayOutputStream(fileBytes + 512);
        body.write(("--" + boundary + CRLF
                        + "Content-Disposition: form-data; name=\"file\"; filename=\"big.bin\"" + CRLF
                        + "Content-Type: application/octet-stream" + CRLF + CRLF)
                .getBytes(StandardCharsets.US_ASCII));
        body.write(new byte[fileBytes]);
        body.write((CRLF + "--" + boundary + "--" + CRLF).getBytes(StandardCharsets.US_ASCII));
        return rawRequest(
                "POST " + path + " HTTP/1.1",
                body.toByteArray(),
                MediaType.MULTIPART_FORM_DATA_VALUE + "; boundary=" + boundary);
    }

    /** Sends one HTTP/1.1 request and reads a single length-delimited response, failing on a truncated response. */
    private RawResponse rawRequest(String requestLine, byte[] body, String contentType) throws Exception {
        StringBuilder head = new StringBuilder(requestLine)
                .append(CRLF)
                .append("Host: localhost")
                .append(CRLF)
                .append("Accept: application/json, application/problem+json")
                .append(CRLF)
                .append("Connection: close")
                .append(CRLF);
        if (contentType != null) {
            head.append("Content-Type: ").append(contentType).append(CRLF);
        }
        if (body.length > 0) {
            head.append("Content-Length: ").append(body.length).append(CRLF);
        }
        head.append(CRLF);

        ExecutorService writer = Executors.newSingleThreadExecutor();
        try (Socket socket = new Socket("localhost", port)) {
            socket.setSoTimeout(15_000);
            OutputStream out = socket.getOutputStream();
            out.write(head.toString().getBytes(StandardCharsets.US_ASCII));
            out.flush();
            Future<?> upload = writer.submit(() -> writeTolerantly(out, body));
            RawResponse response = readResponse(socket.getInputStream());
            upload.cancel(true);
            return response;
        } finally {
            writer.shutdownNow();
        }
    }

    /** Writes the request body; the server may legitimately stop reading and close the connection mid-upload. */
    private static void writeTolerantly(OutputStream out, byte[] body) {
        int chunk = 64 * 1024;
        try {
            for (int offset = 0; offset < body.length; offset += chunk) {
                out.write(body, offset, Math.min(chunk, body.length - offset));
            }
            out.flush();
        } catch (IOException ex) {
            // Server closed the connection after responding; the response is read on the calling thread.
        }
    }

    private static RawResponse readResponse(InputStream in) throws IOException {
        ByteArrayOutputStream received = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int headerEnd = -1;
        Integer contentLength = null;
        Map<String, String> headers = new LinkedHashMap<>();
        int status = -1;
        try {
            for (int read = in.read(buffer); read != -1; read = in.read(buffer)) {
                received.write(buffer, 0, read);
                byte[] bytes = received.toByteArray();
                if (headerEnd < 0) {
                    headerEnd = indexOf(bytes, (CRLF + CRLF).getBytes(StandardCharsets.US_ASCII));
                    if (headerEnd >= 0) {
                        String[] lines = new String(bytes, 0, headerEnd, StandardCharsets.ISO_8859_1).split(CRLF);
                        status = Integer.parseInt(lines[0].split(" ")[1]);
                        for (int i = 1; i < lines.length; i++) {
                            int colon = lines[i].indexOf(':');
                            headers.put(
                                    lines[i].substring(0, colon).trim().toLowerCase(Locale.ROOT),
                                    lines[i].substring(colon + 1).trim());
                        }
                        String length = headers.get("content-length");
                        contentLength = length == null ? null : Integer.valueOf(length);
                    }
                }
                if (contentLength != null && bytes.length >= headerEnd + 4 + contentLength) {
                    break;
                }
            }
        } catch (SocketException ex) {
            // Reported below as a truncated response if the body did not arrive completely.
        }
        byte[] bytes = received.toByteArray();
        assertThat(headerEnd)
                .as("response headers received: %s", new String(bytes, StandardCharsets.ISO_8859_1))
                .isNotNegative();
        assertThat(contentLength).as("Content-Length of %s", headers).isNotNull();
        assertThat(bytes.length - headerEnd - 4)
                .as("complete response body received")
                .isGreaterThanOrEqualTo(contentLength);
        byte[] responseBody = Arrays.copyOfRange(bytes, headerEnd + 4, headerEnd + 4 + contentLength);
        return new RawResponse(status, headers, responseBody);
    }

    private static int indexOf(byte[] haystack, byte[] needle) {
        for (int i = 0; i <= haystack.length - needle.length; i++) {
            if (Arrays.equals(haystack, i, i + needle.length, needle, 0, needle.length)) {
                return i;
            }
        }
        return -1;
    }

    private static void assertLengthDelimited(RawResponse response) {
        assertThat(response.headers()).doesNotContainKey("transfer-encoding");
        assertThat(response.headers()).containsEntry("content-length", String.valueOf(response.body().length));
        assertThat(response.body()).isNotEmpty();
    }

    /** A response read from the wire; header names are lower-cased. */
    private record RawResponse(int status, Map<String, String> headers, byte[] body) {

        ResponseEntity<Map<String, Object>> toEntity() {
            HttpHeaders httpHeaders = new HttpHeaders();
            headers.forEach(httpHeaders::add);
            Map<String, Object> json = JSON.readValue(body, JSON_MAP_TYPE);
            return ResponseEntity.status(status).headers(httpHeaders).body(json);
        }
    }

    /**
     * File size just above the app's effective {@code max-file-size}, read at runtime so this test never overrides
     * app-wide {@code spring.servlet.multipart.*} settings that other features validate.
     *
     * <p>Exceeding the file limit by a small margin (rather than sending a body far above {@code max-request-size})
     * keeps the unread remainder below Tomcat's swallow limit; otherwise Tomcat may reset the connection before the
     * client reads the 413 response, making the test flaky.
     */
    private int oversizedUploadBytes() {
        long maxFileSize = multipartProperties.getMaxFileSize().toBytes();
        assertThat(maxFileSize)
                .as("spring.servlet.multipart.max-file-size must be limited")
                .isPositive();
        return Math.toIntExact(maxFileSize + DataSize.ofKilobytes(1).toBytes());
    }

    private static void assertProblem(
            ResponseEntity<Map<String, Object>> response, HttpStatus status, String instance) {
        assertThat(response.getStatusCode()).isEqualTo(status);
        assertThat(response.getHeaders().getContentType()).isNotNull();
        assertThat(response.getHeaders().getContentType().isCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .isTrue();
        Map<String, Object> body = response.getBody();
        assertThat(body).isNotNull().containsKeys("type", "title", "status", "detail", "instance");
        assertThat(body.get("type")).isEqualTo("urn:problem-type:common:" + status.value());
        assertThat(body.get("title")).isEqualTo(status.getReasonPhrase());
        assertThat(body.get("status")).isEqualTo(status.value());
        assertThat((String) body.get("detail")).isNotBlank();
        assertThat(body.get("instance")).isEqualTo(instance);
    }

    private static void assertNoInternalsLeaked(ResponseEntity<Map<String, Object>> response) {
        String detail = (String) response.getBody().get("detail");
        assertThat(detail)
                .doesNotContain(SECRET)
                .doesNotContain("Exception")
                .doesNotContain("java.")
                .doesNotContain("com.example");
        assertThat(response.getBody().toString()).doesNotContain(SECRET).doesNotContain("trace");
    }

    @RestController
    static class ErrorProbeController {

        @GetMapping("/test-errors/get-post")
        String getPostGet() {
            return "get";
        }

        @PostMapping("/test-errors/get-post")
        String getPostPost() {
            return "post";
        }

        @GetMapping("/test-errors/ok")
        String ok() {
            return "ok";
        }

        @PostMapping("/test-errors/upload")
        String upload(@RequestParam("file") MultipartFile file) {
            return String.valueOf(file.getSize());
        }

        @GetMapping("/test-errors/stream")
        void stream(HttpServletResponse response) throws IOException {
            response.setContentType(MediaType.TEXT_PLAIN_VALUE);
            response.getOutputStream().write(STREAM_FIRST.getBytes(StandardCharsets.UTF_8));
            response.flushBuffer();
            response.getOutputStream().write(STREAM_SECOND.getBytes(StandardCharsets.UTF_8));
        }

        @GetMapping("/test-errors/boom")
        String boom() {
            throw new IllegalStateException(SECRET);
        }

        @PostMapping("/test-errors/boom")
        String boomPost() {
            throw new IllegalStateException(SECRET);
        }

        @DeleteMapping("/test-errors/send-error-404")
        void sendError404(HttpServletResponse response) throws IOException {
            response.sendError(HttpStatus.NOT_FOUND.value(), SECRET);
        }

        @PutMapping("/test-errors/send-error-503")
        void sendError503Put(HttpServletResponse response) throws IOException {
            response.sendError(HttpStatus.SERVICE_UNAVAILABLE.value(), SECRET);
        }

        @GetMapping("/test-errors/send-error-503")
        void sendError503(HttpServletResponse response) throws IOException {
            response.sendError(HttpStatus.SERVICE_UNAVAILABLE.value(), SECRET);
        }

        @GetMapping("/test-errors/send-error-409")
        void sendError409(HttpServletResponse response) throws IOException {
            response.sendError(HttpStatus.CONFLICT.value(), SECRET);
        }

        @GetMapping("/test-errors/send-error-413")
        void sendError413(HttpServletResponse response) throws IOException {
            response.sendError(HttpStatus.CONTENT_TOO_LARGE.value(), SECRET);
        }

        @GetMapping("/test-errors/send-error-499")
        void sendError499(HttpServletResponse response) throws IOException {
            response.sendError(499, SECRET);
        }

        @GetMapping("/test-errors/send-error-600")
        void sendError600(HttpServletResponse response) throws IOException {
            response.sendError(600, SECRET);
        }

        @GetMapping("/test-errors/partial")
        void partial(HttpServletResponse response) throws IOException {
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setCharacterEncoding(StandardCharsets.UTF_8.name());
            response.getOutputStream().write(PARTIAL_BODY.getBytes(StandardCharsets.UTF_8));
            response.flushBuffer();
            throw new IllegalStateException(SECRET);
        }

        @GetMapping("/test-errors/handled")
        String handled() {
            throw new FeatureHandledException();
        }
    }

    static class FeatureHandledException extends RuntimeException {}

    @RestControllerAdvice(assignableTypes = ErrorProbeController.class)
    static class ErrorProbeHandler {

        @ExceptionHandler(FeatureHandledException.class)
        ProblemDetail handle() {
            ProblemDetail problem =
                    ProblemDetail.forStatusAndDetail(HttpStatus.UNPROCESSABLE_CONTENT, "feature detail");
            problem.setType(URI.create("urn:problem-type:test:handled"));
            problem.setTitle("Handled by feature");
            problem.setInstance(URI.create("/test-errors/handled"));
            return problem;
        }
    }

    /** Records the request header names seen by the error dispatch after {@link CommonMultipartErrorDispatchFilter}. */
    static class ErrorDispatchHeaderProbe {
        final AtomicReference<List<String>> headerNames = new AtomicReference<>();
    }

    /** Records how many body bytes the error dispatch wrote, keyed by the original request URI. */
    static class ErrorDispatchOutputProbe {
        final Map<String, Long> bytesWrittenByUri = new ConcurrentHashMap<>();
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class ErrorDispatchProbeConfig {

        @Bean
        ErrorDispatchHeaderProbe errorDispatchHeaderProbe() {
            return new ErrorDispatchHeaderProbe();
        }

        @Bean
        ErrorDispatchOutputProbe errorDispatchOutputProbe() {
            return new ErrorDispatchOutputProbe();
        }

        @Bean
        FilterRegistrationBean<Filter> errorDispatchOutputProbeFilter(ErrorDispatchOutputProbe probe) {
            Filter filter = (request, response, chain) -> {
                CountingResponse counting = new CountingResponse((HttpServletResponse) response);
                try {
                    chain.doFilter(request, counting);
                } finally {
                    Object uri = request.getAttribute(RequestDispatcher.ERROR_REQUEST_URI);
                    if (uri instanceof String path) {
                        probe.bytesWrittenByUri.put(path, counting.count.get());
                    }
                }
            };
            FilterRegistrationBean<Filter> registration = new FilterRegistrationBean<>(filter);
            registration.setDispatcherTypes(DispatcherType.ERROR, DispatcherType.INCLUDE);
            registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 2);
            return registration;
        }

        @Bean
        FilterRegistrationBean<Filter> errorDispatchHeaderProbeFilter(ErrorDispatchHeaderProbe probe) {
            Filter filter = (request, response, chain) -> {
                probe.headerNames.set(Collections.list(((HttpServletRequest) request).getHeaderNames()));
                chain.doFilter(request, response);
            };
            FilterRegistrationBean<Filter> registration = new FilterRegistrationBean<>(filter);
            registration.setDispatcherTypes(DispatcherType.ERROR);
            registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 1);
            return registration;
        }
    }

    /** Counts bytes written to the response body (output stream and writer). */
    static final class CountingResponse extends HttpServletResponseWrapper {

        final AtomicLong count = new AtomicLong();

        CountingResponse(HttpServletResponse response) {
            super(response);
        }

        @Override
        public ServletOutputStream getOutputStream() throws IOException {
            ServletOutputStream delegate = super.getOutputStream();
            return new ServletOutputStream() {
                @Override
                public void write(int b) throws IOException {
                    count.incrementAndGet();
                    delegate.write(b);
                }

                @Override
                public void write(byte[] b, int off, int len) throws IOException {
                    count.addAndGet(len);
                    delegate.write(b, off, len);
                }

                @Override
                public void flush() throws IOException {
                    delegate.flush();
                }

                @Override
                public boolean isReady() {
                    return delegate.isReady();
                }

                @Override
                public void setWriteListener(WriteListener listener) {
                    delegate.setWriteListener(listener);
                }
            };
        }

        @Override
        public PrintWriter getWriter() throws IOException {
            PrintWriter delegate = super.getWriter();
            return new PrintWriter(delegate) {
                @Override
                public void write(int c) {
                    count.incrementAndGet();
                    super.write(c);
                }

                @Override
                public void write(char[] buf, int off, int len) {
                    count.addAndGet(len);
                    super.write(buf, off, len);
                }

                @Override
                public void write(String s, int off, int len) {
                    count.addAndGet(len);
                    super.write(s, off, len);
                }
            };
        }
    }
}
