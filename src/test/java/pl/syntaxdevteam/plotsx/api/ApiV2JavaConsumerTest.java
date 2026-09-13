package pl.syntaxdevteam.plotsx.api;

import org.junit.Test;
import java.util.List;
import java.util.UUID;
import static org.junit.Assert.*;

public class ApiV2JavaConsumerTest {
    @Test public void consumesClassicAndChunkSnapshotsWithoutUnboxingNullRadius() {
        PlotSnapshot classic = new PlotSnapshot(1, UUID.randomUUID(), "world", 0, 64, 0, 1, "classic", 0);
        assertEquals("classic", classic.getGeometryType());
        assertEquals(9L, classic.getArea());
        PlotSnapshot chunks = new PlotSnapshot(2, UUID.randomUUID(), "world", -1, 64, -1, null, "chunks", 0,
                List.of(), List.of(new ChunkSnapshot(-1, -1), new ChunkSnapshot(-2, -1)), 3);
        assertNull(chunks.getRadius());
        assertEquals("chunks", chunks.getGeometryType());
        assertEquals(512L, chunks.getArea());
        assertEquals(3L, chunks.getGeometryRevision());
        assertTrue(chunks.contains("WORLD", -32, -16));
        assertFalse(chunks.contains("world", 0, -1));
    }
    @Test public void explicitlyRequiresV2BoxedRadiusContract() throws Exception {
        assertEquals(Integer.class, PlotSnapshot.class.getMethod("getRadius").getReturnType());
    }
    @Test public void v1BinaryMustBeRecompiledBeforeReadingV2Snapshots() throws Exception {
        var directory = java.nio.file.Files.createTempDirectory("plotsx-v1-consumer");
        try {
            var stub = directory.resolve("PlotSnapshot.java");
            var consumer = directory.resolve("LegacyConsumer.java");
            java.nio.file.Files.writeString(stub, "package pl.syntaxdevteam.plotsx.api; public class PlotSnapshot { public int getRadius() { return 1; } }");
            java.nio.file.Files.writeString(consumer, "public class LegacyConsumer { public static int radius(pl.syntaxdevteam.plotsx.api.PlotSnapshot p) { return p.getRadius(); } }");
            int result = javax.tools.ToolProvider.getSystemJavaCompiler().run(null, null, null,
                    "-d", directory.toString(), stub.toString(), consumer.toString());
            assertEquals(0, result);
            try (var loader = new java.net.URLClassLoader(new java.net.URL[]{directory.toUri().toURL()}, getClass().getClassLoader())) {
                var method = loader.loadClass("LegacyConsumer").getMethod("radius", PlotSnapshot.class);
                var snapshot = new PlotSnapshot(1, UUID.randomUUID(), "world", 0,64,0,1,"home",0);
                var error = assertThrows(java.lang.reflect.InvocationTargetException.class, () -> method.invoke(null, snapshot));
                assertTrue(error.getCause() instanceof NoSuchMethodError);
            }
        } finally {
            try (var paths = java.nio.file.Files.walk(directory)) {
                for (var path : paths.sorted(java.util.Comparator.reverseOrder()).toList()) java.nio.file.Files.delete(path);
            }
        }
    }
}
