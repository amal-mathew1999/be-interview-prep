package com.example.mockretest.library;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.AnnotatedBeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.util.ClassUtils;

/**
 * Feature isolation: every top-level type in the library package (including {@code dto}) is {@code Library}-prefixed,
 * so bean names and JPA entity names, which derive from simple class names, never clash once features are merged.
 */
class LibraryNamingTest {

    @Test
    void everyTopLevelTypeInTheLibraryPackageIsFeaturePrefixed() {
        ClassPathScanningCandidateComponentProvider scanner = new ClassPathScanningCandidateComponentProvider(false) {
            @Override
            protected boolean isCandidateComponent(AnnotatedBeanDefinition beanDefinition) {
                return beanDefinition.getMetadata().isIndependent();
            }
        };
        scanner.addIncludeFilter((reader, factory) -> true);

        List<String> names = scanner.findCandidateComponents(LibraryController.class.getPackageName()).stream()
                .map(definition -> ClassUtils.getShortName(definition.getBeanClassName()))
                .filter(name -> !name.contains("."))
                .toList();

        assertThat(names)
                .contains(
                        "LibraryController",
                        "LibraryBook",
                        "LibraryLoan",
                        "LibraryBookRepository",
                        "LibraryLoanRepository",
                        "LibraryBookRequest")
                .allMatch(name -> name.startsWith("Library"));
    }
}
