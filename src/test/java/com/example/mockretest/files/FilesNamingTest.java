package com.example.mockretest.files;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.AnnotatedBeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.util.ClassUtils;

/** Feature isolation: every type in the files package is {@code Files}-prefixed so names never clash once merged. */
class FilesNamingTest {

    @Test
    void everyTopLevelTypeInTheFilesPackageIsFeaturePrefixed() {
        ClassPathScanningCandidateComponentProvider scanner = new ClassPathScanningCandidateComponentProvider(false) {
            @Override
            protected boolean isCandidateComponent(AnnotatedBeanDefinition beanDefinition) {
                return beanDefinition.getMetadata().isIndependent();
            }
        };
        scanner.addIncludeFilter((reader, factory) -> true);

        List<String> names = scanner.findCandidateComponents(FilesController.class.getPackageName()).stream()
                .map(definition -> ClassUtils.getShortName(definition.getBeanClassName()))
                .filter(name -> !name.contains("."))
                .toList();

        assertThat(names).contains("FilesController", "FilesStoredFile").allMatch(name -> name.startsWith("Files"));
    }
}
