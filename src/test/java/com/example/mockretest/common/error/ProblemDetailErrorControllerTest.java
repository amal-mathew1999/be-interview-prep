package com.example.mockretest.common.error;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.client.RestClient;
import org.springframework.web.multipart.MultipartFile;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"spring.servlet.multipart.max-file-size=1KB", "spring.servlet.multipart.max-request-size=2KB"})
@Import({
    ProblemDetailErrorControllerTest.ErrorProbeController.class,
    ProblemDetailErrorControllerTest.ErrorProbeHandler.class
})
class ProblemDetailErrorControllerTest {

    private static final String SECRET = "SecretInternalDetail-42";
    private static final ParameterizedTypeReference<Map<String, Object>> JSON_MAP =
            new ParameterizedTypeReference<>() {};

    @LocalServerPort
    private int port;

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
    void returns413ProblemForMultipartExceedingContainerLimit() {
        MultiValueMap<String, Object> parts = new LinkedMultiValueMap<>();
        parts.add("file", new ByteArrayResource(new byte[8 * 1024]) {
            @Override
            public String getFilename() {
                return "big.bin";
            }
        });

        ResponseEntity<Map<String, Object>> response = client.post()
                .uri("/test-errors/upload")
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(parts)
                .retrieve()
                .toEntity(JSON_MAP);

        assertProblem(response, HttpStatus.CONTENT_TOO_LARGE, "/test-errors/upload");
        assertThat((String) response.getBody().get("detail")).containsIgnoringCase("upload size");
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

        @GetMapping("/test-errors/ok")
        String ok() {
            return "ok";
        }

        @PostMapping("/test-errors/upload")
        String upload(@RequestParam("file") MultipartFile file) {
            return String.valueOf(file.getSize());
        }

        @GetMapping("/test-errors/boom")
        String boom() {
            throw new IllegalStateException(SECRET);
        }

        @GetMapping("/test-errors/send-error-503")
        void sendError503(HttpServletResponse response) throws IOException {
            response.sendError(HttpStatus.SERVICE_UNAVAILABLE.value(), SECRET);
        }

        @GetMapping("/test-errors/send-error-409")
        void sendError409(HttpServletResponse response) throws IOException {
            response.sendError(HttpStatus.CONFLICT.value(), SECRET);
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
}
