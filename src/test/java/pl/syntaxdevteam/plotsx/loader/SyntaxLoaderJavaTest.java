package pl.syntaxdevteam.plotsx.loader;

import org.junit.Test;
import org.junit.Rule;
import org.junit.rules.TemporaryFolder;
import pl.syntaxdevteam.core.SyntaxCore;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.zip.ZipFile;

import static org.junit.Assert.*;

public class SyntaxLoaderJavaTest {
    @Rule public TemporaryFolder temporary = new TemporaryFolder();

    private static final String REPOSITORIES = """
            repositories:
              central: https://repo.maven.apache.org/maven2/
              syntaxdevteam-snapshots: https://nexus.example/snapshots/
              syntaxdevteam-releases: https://nexus.example/releases/
            libraries:
            """;

    private SyntaxLoader.LibraryConfig parse(String libraries) {
        return readYaml(REPOSITORIES + libraries);
    }

    private SyntaxLoader.LibraryConfig readYaml(String yaml) {
        return SyntaxLoader.readLibraryConfig(
                new ByteArrayInputStream(yaml.getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    public void selectsSnapshotReleaseAndCentralRepositories() {
        var config = parse("""
                  - pl.syntaxdevteam:syntaxdb:0.1.0-R0.1-SNAPSHOT
                  - pl.syntaxdevteam:cleanerx:1.5.8
                  - pl.syntaxdevteam.integrations:adapter:1.0.0-SNAPSHOT
                  - org.jetbrains.kotlin:kotlin-stdlib:2.4.20
                  - pl.syntaxdevteamother:library:1.0.0
                """);
        var selected = config.libraries().stream()
                .map(library -> SyntaxLoader.selectRepository(library, config)).toList();
        assertEquals(List.of("syntaxdevteam-snapshots", "syntaxdevteam-releases",
                "syntaxdevteam-snapshots", "central", "central"), selected);
    }

    @Test
    public void keepsSelectedRepositoryFirstAndCentralForTransitives() {
        var config = parse("  - g:a:1\n");
        for (String id : List.of("syntaxdevteam-snapshots", "syntaxdevteam-releases")) {
            var repositories = SyntaxLoader.repositoriesFor(id, config);
            assertEquals(List.of(id, "central"), repositories.stream().map(r -> r.getId()).toList());
            assertEquals(config.repositories().get(id).getUrl(), repositories.getFirst().getUrl());
        }
        assertEquals(List.of(config.repositories().get("central")),
                SyntaxLoader.repositoriesFor("central", config));
    }

    @Test
    public void preservesExplicitRepositorySelection() {
        var config = parse("""
                  - coordinate: pl.syntaxdevteam:syntaxdb:0.1.0-SNAPSHOT
                    repository: central
                  - coordinate: third.party:library:1.0
                    repository: syntaxdevteam-releases
                """);
        assertEquals("central", SyntaxLoader.selectRepository(config.libraries().getFirst(), config));
        assertEquals("syntaxdevteam-releases", SyntaxLoader.selectRepository(config.libraries().get(1), config));
    }

    @Test
    public void rejectsUnknownRepository() {
        assertThrows(IllegalArgumentException.class,
                () -> parse("  - coordinate: g:a:1\n    repository: missing\n"));
        var config = parse("  - g:a:1\n");
        assertThrows(IllegalArgumentException.class, () -> SyntaxLoader.repositoriesFor("missing", config));
    }

    @Test
    public void rejectsUnexpandedAndMalformedCoordinates() {
        assertThrows(IllegalArgumentException.class,
                () -> parse("  - org.jetbrains.kotlin:kotlin-stdlib:${kotlinVersion}\n"));
        assertThrows(IllegalArgumentException.class, () -> parse("  - not-a-coordinate\n"));
        assertThrows(IllegalArgumentException.class, () -> parse("  - coordinate: ''\n"));
    }

    @Test
    public void rejectsMissingRepositoriesAndLibraries() {
        assertThrows(IllegalArgumentException.class, () -> readYaml("libraries: [g:a:1]\n"));
        assertThrows(IllegalArgumentException.class,
                () -> readYaml("repositories: {other: 'https://repo.example/'}\nlibraries: [g:a:1]\n"));
        assertThrows(IllegalArgumentException.class, () -> parse(""));
        assertThrows(IllegalArgumentException.class, () -> readYaml(REPOSITORIES + "  []\n"));
    }

    @Test
    public void rejectsInvalidRepositoryDefinitions() {
        assertThrows(IllegalArgumentException.class,
                () -> readYaml("repositories: {central: 'file:///tmp/repo'}\nlibraries: [g:a:1]\n"));
        assertThrows(IllegalArgumentException.class,
                () -> parse("  - coordinate: g:a:1\n    repository: 123\n"));
    }

    @Test
    public void parsedConfigurationIsImmutable() {
        var config = parse("  - g:a:1\n");
        assertThrows(UnsupportedOperationException.class, () -> config.repositories().clear());
        assertThrows(UnsupportedOperationException.class, () -> config.libraries().clear());
    }

    @Test
    public void runtimeManifestLoadsOneKotlinRuntimeAndBothSyntaxRepositoryTypes() throws IOException {
        try (var input = SyntaxLoader.class.getClassLoader().getResourceAsStream("paper-libraries.yml")) {
            assertNotNull("Processed runtime manifest must be on the test classpath", input);
            var config = SyntaxLoader.readLibraryConfig(input);
            assertEquals(1L, config.libraries().stream()
                    .filter(library -> library.coordinate().startsWith("pl.syntaxdevteam:syntaxcore:"))
                    .count());
            assertEquals(1L, config.libraries().stream()
                    .filter(library -> library.coordinate().startsWith("org.jetbrains.kotlin:kotlin-stdlib:"))
                    .count());
            for (var library : config.libraries()) {
                var selected = SyntaxLoader.selectRepository(library, config);
                assertFalse(SyntaxLoader.repositoriesFor(selected, config).isEmpty());
                if (library.coordinate().startsWith("org.jetbrains.kotlin:kotlin-stdlib:")) {
                    assertEquals("central", selected);
                }
            }
            assertTrue(config.repositories().containsKey("syntaxdevteam-snapshots"));
            assertTrue(config.repositories().containsKey("syntaxdevteam-releases"));
        }
    }

    @Test
    public void filtersDownloadedSyntaxCoreWithoutRemovingItsDependenciesOrMetadata() throws Exception {
        var source = Path.of(SyntaxCore.class.getProtectionDomain().getCodeSource().getLocation().toURI());
        var cache = temporary.newFolder().toPath();
        var prepared = SyntaxCoreLibrary.prepare(source, cache);
        assertEquals(cache.resolve(source.getFileName()), prepared);
        try (var original = new ZipFile(source.toFile()); var filtered = new ZipFile(prepared.toFile())) {
            assertNotNull(original.getEntry("kotlin/jvm/internal/Intrinsics.class"));
            assertNotNull(original.getEntry("net/kyori/adventure/text/Component.class"));
            assertNotNull(filtered.getEntry("pl/syntaxdevteam/core/SyntaxCore.class"));
            assertNotNull(filtered.getEntry("com/fasterxml/jackson/databind/ObjectMapper.class"));
            assertNotNull(filtered.getEntry("META-INF/services/com.fasterxml.jackson.databind.Module"));
            assertNotNull(filtered.getEntry("META-INF/MANIFEST.MF"));
            assertTrue(filtered.stream().noneMatch(entry ->
                    entry.getName().startsWith("kotlin/") || entry.getName().contains("net/kyori/")
                            || entry.getName().startsWith("META-INF/services/net.kyori.")));
        }
        assertEquals(prepared, SyntaxCoreLibrary.prepare(source, cache));
    }
}
