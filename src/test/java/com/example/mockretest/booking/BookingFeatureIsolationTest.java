package com.example.mockretest.booking;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.persistence.Entity;
import java.lang.reflect.Method;
import java.time.Clock;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.AnnotatedBeanDefinition;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.util.ClassUtils;

/** Guards the merge-safety rules: every booking type is feature-prefixed and no raw {@link Clock} bean leaks out. */
class BookingFeatureIsolationTest {

    private static final String BASE_PACKAGE = "com.example.mockretest.booking";

    @Test
    void everyBookingTypeIsFeaturePrefixed() {
        List<String> unprefixed = scanBookingTypes().stream()
                .map(ClassUtils::getShortName)
                .filter(name -> !name.startsWith("Booking"))
                .toList();

        assertThat(unprefixed).isEmpty();
    }

    @Test
    void bookingEntitiesHaveFeaturePrefixedEntityNames() throws ClassNotFoundException {
        for (String className : scanBookingTypes()) {
            Class<?> type = Class.forName(className);
            Entity entity = type.getAnnotation(Entity.class);
            if (entity != null) {
                String entityName = entity.name().isEmpty() ? type.getSimpleName() : entity.name();
                assertThat(entityName).as(className).startsWith("Booking");
            }
        }
    }

    @Test
    void bookingConfigDoesNotExposeRawClockBean() {
        List<Method> clockBeans = Arrays.stream(BookingConfig.class.getDeclaredMethods())
                .filter(method -> method.isAnnotationPresent(Bean.class))
                .filter(method -> Clock.class.isAssignableFrom(method.getReturnType()))
                .toList();

        assertThat(clockBeans).isEmpty();
    }

    private static List<String> scanBookingTypes() {
        ClassPathScanningCandidateComponentProvider scanner = new ClassPathScanningCandidateComponentProvider(false) {
            @Override
            protected boolean isCandidateComponent(AnnotatedBeanDefinition definition) {
                return definition.getMetadata().isIndependent();
            }
        };
        scanner.addIncludeFilter((reader, factory) -> true);
        List<String> types = scanner.findCandidateComponents(BASE_PACKAGE).stream()
                .map(BeanDefinition::getBeanClassName)
                .toList();
        assertThat(types).isNotEmpty();
        return types;
    }
}
