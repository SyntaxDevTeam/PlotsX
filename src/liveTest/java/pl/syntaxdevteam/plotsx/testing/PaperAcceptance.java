package pl.syntaxdevteam.plotsx.testing;

import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.event.block.*;
import org.bukkit.plugin.java.JavaPlugin;
import pl.syntaxdevteam.plotsx.PlotsX;
import pl.syntaxdevteam.plotsx.api.*;
import pl.syntaxdevteam.plotsx.databases.DatabaseHandler;
import kotlin.Unit;
import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.sql.*;
import java.util.*;

/** Run only on a disposable localhost server. Seeds its empty database and stops the server. */
public final class PaperAcceptance extends JavaPlugin {
    private final List<String> checks = new ArrayList<>();
    private void check(boolean result, String label) {
        if (!result) throw new AssertionError(label);
        checks.add("PASS " + label);
    }
    @Override public void onEnable() {
        getServer().getScheduler().runTaskLater(this, () -> {
            try { runChecks(); checks.add("RESULT PASS"); }
            catch (Throwable error) { checks.add("RESULT FAIL " + error); error.printStackTrace(); }
            finally {
                try { Files.write(getDataFolder().toPath().resolve("result.txt"), checks); }
                catch (Exception error) { error.printStackTrace(); }
                getServer().shutdown();
            }
        }, 20L);
        getDataFolder().mkdirs();
    }
    private Player player(UUID uuid, World world) {
        return (Player) Proxy.newProxyInstance(getClassLoader(), new Class<?>[]{Player.class}, (proxy, method, args) -> {
            return switch (method.getName()) {
                case "getUniqueId" -> uuid;
                case "getName" -> "Acceptance";
                case "getWorld" -> world;
                case "getLocation" -> new Location(world, 0, 64, 0);
                case "getEffectivePermissions" -> Set.of();
                case "getGameMode" -> GameMode.SURVIVAL;
                case "hashCode" -> uuid.hashCode();
                case "equals" -> proxy == args[0];
                case "toString" -> "AcceptancePlayer";
                default -> {
                    if (method.getReturnType() == boolean.class) yield false;
                    if (method.getReturnType() == int.class) yield 0;
                    if (method.getReturnType() == long.class) yield 0L;
                    if (method.getReturnType() == float.class) yield 0F;
                    if (method.getReturnType() == double.class) yield 0D;
                    yield null;
                }
            };
        });
    }
    private void runChecks() throws Exception {
        PlotsX plugin = (PlotsX) getServer().getPluginManager().getPlugin("PlotsX");
        check(plugin != null && plugin.isEnabled(), "plugin startup and migration");
        PlotsXApi api = getServer().getServicesManager().load(PlotsXApi.class);
        check(api != null && api.getApiVersion() == 2, "API v2 service");
        boolean reuse = Boolean.getBoolean("plotsx.acceptance.reuse");
        check(reuse ? api.getPlots().size() == 2 : api.getPlots().isEmpty(), reuse ? "persisted mixed plots survive mode restart" : "disposable empty database");
        UUID owner = reuse ? api.getPlot(1).getOwner() : UUID.randomUUID();
        World world = getServer().getWorlds().getFirst();
        DatabaseHandler db = plugin.getDatabaseHandler();
        // Seed both geometries using the migrated schema, before any actor events.
        var connectionMethod = DatabaseHandler.class.getDeclaredMethod("getConnection");
        connectionMethod.setAccessible(true);
        if (!reuse) try (Connection c = (Connection) connectionMethod.invoke(db)) {
            c.setAutoCommit(false);
            try (PreparedStatement s = c.prepareStatement("INSERT INTO plots (plot_id,owner_uuid,x,z,y,radius,world,name,creation_time,geometry_type,geometry_revision) VALUES (?,?,?,?,?,?,?,?,?,?,0)")) {
                for (int id = 1; id <= 2; id++) {
                    s.setInt(1,id); s.setString(2,owner.toString()); s.setInt(3,id == 1 ? -1 : 40); s.setInt(4,id == 1 ? -1 : 40);
                    s.setInt(5,64); if(id == 1) s.setNull(6,Types.INTEGER); else s.setInt(6,2);
                    s.setString(7,world.getName()); s.setString(8,"seed"+id); s.setLong(9,0); s.setString(10,id == 1 ? "chunks" : "classic"); s.executeUpdate();
                }
            }
            try (PreparedStatement s = c.prepareStatement("INSERT INTO plot_chunks (plot_id,world_key,chunk_x,chunk_z) VALUES (1,?,?,?)")) {
                for (int[] chunk : new int[][]{{-1,-1},{-2,-1},{-1,-2}}) {
                    s.setString(1,world.getName().toLowerCase(Locale.ROOT));s.setInt(2,chunk[0]);s.setInt(3,chunk[1]);s.executeUpdate();
                }
            }
            c.commit();
        }
        plugin.getProtectionCoordinator().recover(() -> { plugin.getCacheManager().reloadAllCachesSync(); return Unit.INSTANCE; });
        check(api.getPlotAt(world.getName(),-16,-16).getRadius() == null, "chunk runtime has no artificial radius");
        check(api.getPlotAt(world.getName(),-32,-16).getArea() == 768, "negative boundary and exact area");
        check(api.getPlotAt(world.getName(),-32,-32) == null, "concave corner remains unclaimed");
        check(api.getPlotAt(world.getName(),40,40).getRadius() == 2, "classic coexists");
        Player visitor = player(UUID.randomUUID(),world), member = player(owner,world);
        for (int[] point : new int[][]{{-16,-16},{-1,-1},{40,40}}) {
            Block b = world.getBlockAt(point[0],64,point[1]); b.setType(Material.STONE);
            BlockBreakEvent denied = new BlockBreakEvent(b,visitor); getServer().getPluginManager().callEvent(denied);
            check(denied.isCancelled(), "visitor break denied " + Arrays.toString(point));
            BlockBreakEvent allowed = new BlockBreakEvent(b,member); getServer().getPluginManager().callEvent(allowed);
            check(!allowed.isCancelled(), "owner break allowed " + Arrays.toString(point));
            check(api.evaluateFlag(world.getName(),point[0],point[1],"build",visitor.getUniqueId()) == FlagDecision.DENY, "API agrees with event");
        }
        Block outside = world.getBlockAt(-32,64,-32);
        BlockBreakEvent free = new BlockBreakEvent(outside,visitor); getServer().getPluginManager().callEvent(free);
        check(!free.isCancelled(), "unclaimed hole permits breaking");
        Block source = world.getBlockAt(0,64,-1);
        BlockPistonExtendEvent piston = new BlockPistonExtendEvent(source.getRelative(BlockFace.EAST),List.of(source),BlockFace.WEST);
        getServer().getPluginManager().callEvent(piston);
        check(piston.isCancelled(), "piston crossing exact chunk edge denied");
        boolean denyFlow = !api.getFlags().stream().filter(f -> f.getKey().equals("flow")).findFirst().orElseThrow().getEnabledMeansAllowed();
        check(db.updatePlotFlag(1, "flow", denyFlow), "flow flag stored");
        BlockFromToEvent flow = new BlockFromToEvent(source, BlockFace.WEST);
        getServer().getPluginManager().callEvent(flow);
        check(flow.isCancelled(), "fluid entering chunk denied");
        java.util.concurrent.CountDownLatch publishing = new java.util.concurrent.CountDownLatch(1);
        java.util.concurrent.CountDownLatch release = new java.util.concurrent.CountDownLatch(1);
        var executor = java.util.concurrent.Executors.newSingleThreadExecutor();
        try {
            var job = executor.submit(() -> plugin.getProtectionCoordinator().mutate(() -> {
                publishing.countDown();
                try { if (!release.await(5, java.util.concurrent.TimeUnit.SECONDS)) throw new IllegalStateException("timeout"); }
                catch (InterruptedException e) { throw new RuntimeException(e); }
                return Unit.INSTANCE;
            }, () -> Unit.INSTANCE));
            check(publishing.await(5, java.util.concurrent.TimeUnit.SECONDS), "mutation enters publication barrier");
            BlockBreakEvent reserved = new BlockBreakEvent(source, visitor);
            getServer().getPluginManager().callEvent(reserved);
            check(reserved.isCancelled(), "real event denied during publication gap");
            check(api.evaluateFlag(world.getName(),0,-1,"build",owner) == FlagDecision.DENY, "API denied during publication gap");
            release.countDown(); job.get(5, java.util.concurrent.TimeUnit.SECONDS);
        } finally { release.countDown(); executor.shutdownNow(); }
        DatabaseHandler.ClaimResult result = db.claimPlotAtomically(owner,owner,world.getName(),128,128,64,2,10,10000,"new",32,64);
        check(result instanceof DatabaseHandler.ClaimResult.Success, "configured claim transaction succeeds");
        PlotSnapshot created = api.getPlotAt(world.getName(),128,128);
        check(created != null && created.getGeometryType().equals(plugin.getClaimMode().name().toLowerCase(Locale.ROOT)), "configured strategy and immediate cache publication");
        check(db.deletePlot(created.getId()), "delete configured plot");
        check(api.getPlotAt(world.getName(),128,128) == null, "delete publishes before returning");
        var configFile = plugin.getDataFolder().toPath().resolve("config.yml");
        String originalConfig = Files.readString(configFile);
        try {
            String mode = plugin.getClaimMode().name().toLowerCase(Locale.ROOT);
            Files.writeString(configFile, originalConfig.replace("mode: " + mode, "mode: " + (mode.equals("chunks") ? "classic" : "chunks")));
            boolean rejected = false;
            try { plugin.onReload(); } catch (IllegalArgumentException expected) { rejected = true; }
            check(rejected, "mode change rejected during reload");
        } finally { Files.writeString(configFile, originalConfig); }
        plugin.onReload();
        check(api.getPlotAt(world.getName(),-1,-1).getGeometryType().equals("chunks"), "reload preserves chunk protection");
        check(api.getPlotAt(world.getName(),40,40).getGeometryType().equals("classic"), "reload preserves classic protection");
        checks.add("SERVER " + getServer().getVersion());
        checks.add("MODE " + plugin.getClaimMode());
    }
}
