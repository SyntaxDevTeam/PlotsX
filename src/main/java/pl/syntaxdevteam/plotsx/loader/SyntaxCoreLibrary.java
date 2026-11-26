package pl.syntaxdevteam.plotsx.loader;

import io.papermc.paper.plugin.loader.library.LibraryLoadingException;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

final class SyntaxCoreLibrary {

    static Path prepare(Path source, Path cache) {
        try (var jar = new ZipFile(source.toFile())) {
            // The resolver also reports transitive dependencies. Only transform SyntaxCore itself.
            if (jar.getEntry("pl/syntaxdevteam/core/SyntaxCore.class") == null) return source;

            Files.createDirectories(cache);
            var target = cache.resolve(source.getFileName());
            var temporary = Files.createTempFile(cache, "syntaxcore-", ".tmp");
            try {
                try (var output = new ZipOutputStream(Files.newOutputStream(temporary))) {
                    var entries = jar.entries();
                    while (entries.hasMoreElements()) {
                        var original = entries.nextElement();
                        if (excluded(original.getName())) continue;
                        var entry = new ZipEntry(original.getName());
                        entry.setTime(original.getTime());
                        output.putNextEntry(entry);
                        try (var input = jar.getInputStream(original)) {
                            input.transferTo(output);
                        }
                        output.closeEntry();
                    }
                }
                try {
                    return Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
                } catch (AtomicMoveNotSupportedException unsupported) {
                    return Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
                }
            } finally {
                Files.deleteIfExists(temporary);
            }
        } catch (IOException failure) {
            throw new LibraryLoadingException("Cannot prepare SyntaxCore library " + source, failure);
        }
    }

    private static boolean excluded(String name) {
        // Paper provides Adventure and the explicit runtime library provides Kotlin.
        return name.startsWith("kotlin/") || name.startsWith("net/kyori/")
                || name.matches("META-INF/versions/[0-9]+/(kotlin|net/kyori)/.*")
                || name.startsWith("META-INF/services/net.kyori.")
                || name.matches("META-INF/[^/]+\\.(kotlin_module|SF|RSA|DSA|EC)");
    }
}
