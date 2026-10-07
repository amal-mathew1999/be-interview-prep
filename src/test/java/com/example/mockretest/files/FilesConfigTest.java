package com.example.mockretest.files;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.mockretest.files.FilesConfig.FilesMultipartLimits;
import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.util.unit.DataSize;

/** Configuration wiring: where the files settings live and the startup check coupling them to the container limits. */
class FilesConfigTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withInitializer(new ConfigDataApplicationContextInitializer())
            .withUserConfiguration(FilesConfig.class);

    private static Properties load(String resource) throws IOException {
        Properties properties = new Properties();
        try (InputStream in = FilesConfigTest.class.getClassLoader().getResourceAsStream(resource)) {
            assertThat(in).as(resource).isNotNull();
            properties.load(in);
        }
        return properties;
    }

    @Test
    void filesPropertiesContainsOnlyFeatureKeys() throws IOException {
        assertThat(load("files.properties").stringPropertyNames()).allMatch(key -> key.startsWith("files."));
    }

    @Test
    void containerMultipartLimitsAreAppLevelSettingsAboveTheFilesLimit() throws IOException {
        Properties application = load("application.properties");
        DataSize maxSize = DataSize.parse(load("files.properties").getProperty("files.max-size"));

        assertThat(DataSize.parse(application.getProperty(FilesConfig.MAX_FILE_SIZE_KEY)))
                .isGreaterThanOrEqualTo(maxSize);
        assertThat(DataSize.parse(application.getProperty(FilesConfig.MAX_REQUEST_SIZE_KEY)))
                .isGreaterThan(maxSize);
    }

    @Test
    void defaultStorageDirIsNotUnderTheOsTempDirectory() throws IOException {
        assertThat(load("files.properties").getProperty("files.storage-dir"))
                .doesNotContain("java.io.tmpdir")
                .isEqualTo("./data/files");
    }

    @Test
    void startsWithTheShippedConfiguration() {
        runner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context.getBean(FilesMultipartLimits.class).maxFileSize())
                    .isEqualTo(DataSize.ofMegabytes(6));
        });
    }

    @Test
    void failsStartupWhenFilesLimitExceedsContainerFileLimit() {
        runner.withPropertyValues("files.max-size=20MB", "spring.servlet.multipart.max-request-size=30MB")
                .run(context -> assertThat(context)
                        .hasFailed()
                        .getFailure()
                        .rootCause()
                        .hasMessageContaining("spring.servlet.multipart.max-file-size")
                        .hasMessageContaining("files.max-size"));
    }

    @Test
    void failsStartupWhenFilesLimitExceedsContainerRequestLimit() {
        runner.withPropertyValues("files.max-size=7MB", "spring.servlet.multipart.max-file-size=8MB")
                .run(context -> assertThat(context)
                        .hasFailed()
                        .getFailure()
                        .rootCause()
                        .hasMessageContaining("spring.servlet.multipart.max-request-size"));
    }

    @Test
    void unlimitedContainerLimitsAlwaysAllowTheFilesLimit() {
        new FilesMultipartLimits(DataSize.ofBytes(-1), DataSize.ofBytes(-1)).verifyAllows(DataSize.ofGigabytes(1));
        assertThatThrownBy(() -> new FilesMultipartLimits(DataSize.ofMegabytes(1), DataSize.ofBytes(-1))
                        .verifyAllows(DataSize.ofMegabytes(5)))
                .isInstanceOf(IllegalStateException.class);
    }
}
