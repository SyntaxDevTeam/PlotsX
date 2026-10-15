package pl.syntaxdevteam.plotsx.loader;

import io.papermc.paper.plugin.loader.PluginClasspathBuilder;
import io.papermc.paper.plugin.loader.PluginLoader;
import io.papermc.paper.plugin.loader.library.impl.MavenLibraryResolver;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.graph.Dependency;
import org.eclipse.aether.repository.RemoteRepository;
import org.yaml.snakeyaml.Yaml;

import java.io.InputStream;
import java.util.*;

/** Java bootstrap: Kotlin is resolved before any Kotlin plugin code is loaded. */
@SuppressWarnings({"UnstableApiUsage", "unused"})
public final class SyntaxLoader implements PluginLoader {
    @Override
    public void classloader(PluginClasspathBuilder classpath) {
        final LibraryConfig config;
        try (InputStream input = SyntaxLoader.class.getClassLoader().getResourceAsStream("paper-libraries.yml")) {
            if (input == null) throw new IllegalStateException("paper-libraries.yml not found in plugin JAR");
            config = readLibraryConfig(input);
        } catch (Exception failure) {
            throw new IllegalStateException("Cannot configure PlotsX runtime libraries from paper-libraries.yml", failure);
        }
        Map<String, MavenLibraryResolver> resolvers = new LinkedHashMap<>();
        for (LibrarySpec library : config.libraries()) {
            String repositoryId = selectRepository(library, config);
            MavenLibraryResolver resolver = resolvers.computeIfAbsent(repositoryId, key -> {
                MavenLibraryResolver created = new MavenLibraryResolver();
                repositoriesFor(key, config).forEach(created::addRepository);
                return created;
            });
            resolver.addDependency(new Dependency(new DefaultArtifact(library.coordinate()), null));
        }
        resolvers.values().forEach(classpath::addLibrary);
    }

    static LibraryConfig readLibraryConfig(InputStream input) {
        Object raw = new Yaml().load(input);
        if (!(raw instanceof Map<?, ?> document)) throw new IllegalArgumentException("paper-libraries.yml must contain a mapping");
        if (!(document.get("repositories") instanceof Map<?, ?> repositoryData)) throw new IllegalArgumentException("paper-libraries.yml must define repositories");
        Map<String, RemoteRepository> repositories = new LinkedHashMap<>();
        repositoryData.forEach((key, value) -> {
            if (!(key instanceof String id) || id.isBlank() || !(value instanceof String configuredUrl) || configuredUrl.isBlank())
                throw new IllegalArgumentException("Invalid repository entry: " + key);
            String url = configuredUrl.trim().equals("MAVEN_CENTRAL_DEFAULT_MIRROR")
                ? MavenLibraryResolver.MAVEN_CENTRAL_DEFAULT_MIRROR : configuredUrl.trim();
            if (!url.startsWith("https://") && !url.startsWith("http://")) throw new IllegalArgumentException("Invalid repository URL for " + id);
            repositories.put(id.trim(), new RemoteRepository.Builder(id.trim(), "default", url).build());
        });
        if (!repositories.containsKey("central")) throw new IllegalArgumentException("Missing central repository in paper-libraries.yml");
        if (!(document.get("libraries") instanceof List<?> libraryData) || libraryData.isEmpty()) throw new IllegalArgumentException("No runtime libraries defined in paper-libraries.yml");
        List<LibrarySpec> libraries = new ArrayList<>();
        for (Object entry : libraryData) {
            final LibrarySpec library;
            if (entry instanceof String coordinate) library = new LibrarySpec(coordinate.trim(), null);
            else if (entry instanceof Map<?, ?> map && map.get("coordinate") instanceof String coordinate) {
                Object repository = map.get("repository");
                if (repository != null && !(repository instanceof String)) throw new IllegalArgumentException("Invalid repository in library entry");
                library = new LibrarySpec(coordinate.trim(), repository == null ? null : ((String) repository).trim());
            } else throw new IllegalArgumentException("Invalid library entry");
            if (library.coordinate().isEmpty() || library.coordinate().contains("${")) throw new IllegalArgumentException("Invalid or unexpanded library coordinate: " + library.coordinate());
            new DefaultArtifact(library.coordinate());
            libraries.add(library);
        }
        LibraryConfig config = new LibraryConfig(Collections.unmodifiableMap(repositories), List.copyOf(libraries));
        libraries.forEach(library -> selectRepository(library, config));
        return config;
    }

    static String selectRepository(LibrarySpec library, LibraryConfig config) {
        String repositoryId = library.repository();
        if (repositoryId == null) {
            String group = library.coordinate().substring(0, library.coordinate().indexOf(':'));
            repositoryId = group.equals("pl.syntaxdevteam") || group.startsWith("pl.syntaxdevteam.")
                ? library.coordinate().endsWith("-SNAPSHOT") ? "syntaxdevteam-snapshots" : "syntaxdevteam-releases"
                : "central";
        }
        if (!config.repositories().containsKey(repositoryId)) throw new IllegalArgumentException("Unknown repository '" + repositoryId + "' for " + library.coordinate());
        return repositoryId;
    }

    static List<RemoteRepository> repositoriesFor(String repositoryId, LibraryConfig config) {
        RemoteRepository selected = config.repositories().get(repositoryId);
        if (selected == null) throw new IllegalArgumentException("Unknown repository: " + repositoryId);
        return repositoryId.equals("central") ? List.of(selected) : List.of(selected, config.repositories().get("central"));
    }

    record LibraryConfig(Map<String, RemoteRepository> repositories, List<LibrarySpec> libraries) {}
    record LibrarySpec(String coordinate, String repository) {}
}
