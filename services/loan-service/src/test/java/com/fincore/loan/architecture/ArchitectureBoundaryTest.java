package com.fincore.loan.architecture;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

class ArchitectureBoundaryTest {

    private static final Set<String> FORBIDDEN_DOMAIN_IMPORTS = Set.of(
            "org.springframework",
            "org.springframework.jdbc",
            "org.springframework.kafka",
            "org.springframework.data",
            "org.elasticsearch",
            "co.elastic",
            "org.mongodb",
            "com.mongodb");

    @Test
    void domainMustNotDependOnFrameworksOrInfrastructure() throws IOException {
        List<String> violations;
        try (Stream<Path> files = Files.walk(Path.of("src/main/java/com/fincore/loan/domain"))) {
            violations = files
                    .filter(path -> path.toString().endsWith(".java"))
                    .flatMap(this::forbiddenImports)
                    .toList();
        }

        assertTrue(violations.isEmpty(), () -> "Domain boundary violations: " + violations);
    }

    @Test
    void controllersMustNotDependOnDomainModelsOrInfrastructureImplementations() throws IOException {
        List<String> violations;
        try (Stream<Path> files = Files.walk(Path.of("src/main/java/com/fincore/loan/api/controller"))) {
            violations = files
                    .filter(path -> path.toString().endsWith(".java"))
                    .flatMap(this::controllerBoundaryViolations)
                    .toList();
        }

        assertTrue(violations.isEmpty(), () -> "Controller boundary violations: " + violations);
    }

    private Stream<String> forbiddenImports(Path source) {
        try {
            return Files.readAllLines(source).stream()
                    .filter(line -> line.startsWith("import "))
                    .filter(line -> FORBIDDEN_DOMAIN_IMPORTS.stream().anyMatch(line::contains))
                    .map(line -> source + ":" + line.trim());
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot inspect architecture source " + source, exception);
        }
    }

    private Stream<String> controllerBoundaryViolations(Path source) {
        try {
            return Files.readAllLines(source).stream()
                    .filter(line -> line.startsWith("import "))
                    .filter(line -> line.contains(".domain.model.") || line.contains(".infrastructure."))
                    .map(line -> source + ":" + line.trim());
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot inspect architecture source " + source, exception);
        }
    }
}
