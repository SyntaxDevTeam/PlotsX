package pl.syntaxdevteam.plotsx.loader;

import org.junit.Test;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import static org.junit.Assert.*;

public class SyntaxLoaderTest {
    private SyntaxLoader.LibraryConfig parse(String libraries) {
        String yaml = "repositories:\n  central: https://repo.maven.apache.org/maven2/\n  syntaxdevteam-snapshots: https://nexus.example/snapshots/\n  syntaxdevteam-releases: https://nexus.example/releases/\nlibraries:\n" + libraries;
        return SyntaxLoader.readLibraryConfig(new ByteArrayInputStream(yaml.getBytes(StandardCharsets.UTF_8)));
    }
    @Test public void selectsRepositoriesAndCentralTransitivesWithoutKotlinBootstrap() {
        var config = parse("  - pl.syntaxdevteam:syntaxdb:0.1.0-SNAPSHOT\n  - org.jetbrains.kotlin:kotlin-stdlib:2.4.20\n");
        assertEquals("syntaxdevteam-snapshots", SyntaxLoader.selectRepository(config.libraries().getFirst(), config));
        assertEquals("central", SyntaxLoader.selectRepository(config.libraries().get(1), config));
        assertEquals(2, SyntaxLoader.repositoriesFor("syntaxdevteam-snapshots", config).size());
        assertEquals(1, SyntaxLoader.repositoriesFor("central", config).size());
    }
    @Test public void preservesExplicitRepositorySelection() {
        var config = parse("  - coordinate: pl.syntaxdevteam:syntaxdb:0.1.0-SNAPSHOT\n    repository: central\n");
        assertEquals("central", SyntaxLoader.selectRepository(config.libraries().getFirst(), config));
    }
    @Test public void rejectsUnknownRepositoryAndUnexpandedCoordinates() {
        assertThrows(IllegalArgumentException.class, () -> parse("  - coordinate: g:a:1\n    repository: missing\n"));
        assertThrows(IllegalArgumentException.class, () -> parse("  - org.jetbrains.kotlin:kotlin-stdlib:${kotlinVersion}\n"));
    }
    @Test public void runtimeManifestExplicitlyLoadsOneKotlinRuntime() {
        try (var input = SyntaxLoader.class.getClassLoader().getResourceAsStream("paper-libraries.yml")) {
            assertNotNull(input);
            var config = SyntaxLoader.readLibraryConfig(input);
            assertEquals(1L, config.libraries().stream().filter(l -> l.coordinate().startsWith("org.jetbrains.kotlin:kotlin-stdlib:")).count());
        } catch (java.io.IOException error) { throw new AssertionError(error); }
    }
}
