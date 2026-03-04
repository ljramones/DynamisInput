package org.dynamisinput.api;

import static org.junit.jupiter.api.Assertions.assertFalse;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

class ArchitectureGuardTest {
    @Test
    void noFundamentalDuplicationTypesInApi() throws IOException {
        var root = Path.of("src/main/java");
        try (Stream<Path> stream = Files.walk(root)) {
            var files = stream
                    .filter(path -> path.toString().endsWith(".java"))
                    .map(path -> path.getFileName().toString().toLowerCase(Locale.ROOT))
                    .toList();

            assertFalse(files.contains("entityid.java"), "Do not duplicate core identity types");
            assertFalse(files.stream().anyMatch(name -> name.contains("vectrix") || name.contains("vector2") || name.contains("vector3")),
                    "No math/vector framework types in input-api");
        }
    }
}
