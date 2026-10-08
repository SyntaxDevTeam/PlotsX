package pl.syntaxdevteam.plotsx.testing;

import kotlin.Unit;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.java.JavaPlugin;
import pl.syntaxdevteam.plotsx.PlotsX;
import pl.syntaxdevteam.plotsx.api.MemberUpdateResult;
import pl.syntaxdevteam.plotsx.commands.PlotMembers;
import pl.syntaxdevteam.plotsx.databases.DatabaseHandler;
import java.nio.file.Files;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/** Disposable localhost fixture, with two real protocol clients named PxOwner and PxRecipient. */
public final class FoliaAcceptance extends JavaPlugin implements Listener {
    private final List<String> checks = new CopyOnWriteArrayList<>();
    private final AtomicBoolean finished = new AtomicBoolean();
    private final AtomicInteger positioned = new AtomicInteger();
    private volatile Player owner, recipient;
    private void check(boolean value, String label) {
        if (!value) throw new AssertionError(label);
        checks.add("PASS " + label);
    }
    @Override public void onEnable() {
        getDataFolder().mkdirs();
        getServer().getPluginManager().registerEvents(this, this);
        getServer().getGlobalRegionScheduler().runDelayed(this, task -> finish(new AssertionError("acceptance timeout")), 2400L);
    }
    @EventHandler public void join(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        boolean isOwner = player.getName().equals("PxOwner");
        if (!isOwner && !player.getName().equals("PxRecipient")) return;
        if (isOwner) owner = player; else recipient = player;
        player.addAttachment(this, "plotsx.cmd.plot", true);
        player.getScheduler().runDelayed(this, task -> {
            try {
                player.setGameMode(org.bukkit.GameMode.CREATIVE);
                player.teleportAsync(new Location(player.getWorld(), isOwner ? 0.5 : 2048.5, -59, 0.5)).whenComplete((success, failure) -> {
                    if (failure != null || !success) { finish(failure == null ? new AssertionError("teleport failed") : failure); return; }
                    if (positioned.incrementAndGet() == 2) prepare();
                });
            } catch (Throwable error) { finish(error); }
        }, () -> finish(new AssertionError("client retired before teleport")), 20L);
    }
    private void prepare() {
        PlotsX plugin = (PlotsX) getServer().getPluginManager().getPlugin("PlotsX");
        try { check(plugin != null && plugin.isEnabled(), "Folia startup"); }
        catch (Throwable error) { finish(error); return; }
        owner.getScheduler().run(this, task -> {
            String world = owner.getWorld().getName();
            var actor = owner.getUniqueId();
            var target = recipient.getUniqueId();
            getServer().getAsyncScheduler().runNow(this, worker -> {
                try {
                    check(plugin.getApi().getPlots().isEmpty(), "disposable empty database");
                    var claim = plugin.getDatabaseHandler().claimPlotAtomically(actor, actor, world, 0, 0, 5, 2, 10, 10000, "test", 32, 64);
                    check(claim instanceof DatabaseHandler.ClaimResult.Success, "Folia worker claim and publication");
                    int id = ((DatabaseHandler.ClaimResult.Success) claim).getPlotId();
                    check(plugin.getDatabaseHandler().addPlotMember(id, target, "member"), "recipient membership");
                    owner.getScheduler().runDelayed(this, ignored -> expand(plugin, id), () -> finish(new AssertionError("owner retired")), 40L);
                } catch (Throwable error) { finish(error); }
            });
        }, () -> finish(new AssertionError("owner retired")));
    }
    private void expand(PlotsX plugin, int id) {
        try {
            plugin.getConfig().set("plots.expansion.price", 0);
            owner.addAttachment(this, "plotsx.plot.expand", true);
            var gui = new pl.syntaxdevteam.plotsx.gui.ExpandGUI(plugin, id);
            plugin.getGuiHandler().registerGui(owner, gui);
            var click = new org.bukkit.event.inventory.InventoryClickEvent(owner.getOpenInventory(),
                org.bukkit.event.inventory.InventoryType.SlotType.CONTAINER, 20,
                org.bukkit.event.inventory.ClickType.LEFT, org.bukkit.event.inventory.InventoryAction.PICKUP_ALL);
            gui.handleClick(click);
            gui.handleClick(click);
            check(click.isCancelled(), "classic expansion GUI consumes confirmation");
            waitExpansion(plugin, id, 0);
        } catch (Throwable error) { finish(error); }
    }
    private void waitExpansion(PlotsX plugin, int id, int attempt) {
        owner.getScheduler().runDelayed(this, task -> {
            try {
                var plot = plugin.getApi().getPlot(id);
                if (plot.getArea() == 25 && attempt < 20) { waitExpansion(plugin, id, attempt + 1); return; }
                check(plot.getArea() == 50 && plot.getExtensions().size() == 1 && plot.getGeometryRevision() == 1,
                    "Folia classic GUI expansion commits once despite duplicate confirmation");
                getServer().getAsyncScheduler().runNow(this, worker -> {
                    try {
                        var method = DatabaseHandler.class.getDeclaredMethod("getConnection");
                        method.setAccessible(true);
                        try (var c = (java.sql.Connection) method.invoke(plugin.getDatabaseHandler());
                             var statement = c.prepareStatement("SELECT state, classic_radius, amount FROM plot_operations WHERE plot_id = ?")) {
                            statement.setInt(1, id);
                            try (var rows = statement.executeQuery()) {
                                check(rows.next() && rows.getString(1).equals("LAND_COMMITTED") && rows.getInt(2) == 2 &&
                                    new java.math.BigDecimal(rows.getString(3)).signum() == 0, "classic GUI purchase has durable committed journal");
                                check(!rows.next(), "duplicate GUI confirmation does not create a second operation");
                            }
                        }
                        owner.getScheduler().run(this, ignored -> transfer(plugin, id), () -> finish(new AssertionError("owner retired")));
                    } catch (Throwable error) { finish(error); }
                });
            } catch (Throwable error) { finish(error); }
        }, () -> finish(new AssertionError("owner retired")), 5L);
    }
    private void transfer(PlotsX plugin, int id) {
        try {
            check(Bukkit.isOwnedByCurrentRegion(owner), "actor entity thread");
            check(!Bukkit.isOwnedByCurrentRegion(recipient), "players in distinct Folia regions");
            check(plugin.getApi().transferOwnership(owner, id, recipient.getUniqueId()) == MemberUpdateResult.RECIPIENT_WRONG_THREAD,
                "synchronous API safely rejects cross-region recipient");
            plugin.getApi().transferOwnershipAsync(owner, id, recipient.getUniqueId()).whenComplete((result, failure) -> {
                if (failure != null) { finish(failure); return; }
                recipient.getScheduler().run(this, ignored -> {
                    try {
                        check(result == MemberUpdateResult.UPDATED, "async API cross-region transfer");
                        check(plugin.getApi().getPlot(id).getOwner().equals(recipient.getUniqueId()), "transferred owner published");
                        check(new PlotMembers(plugin).execute(recipient, id,
                            List.of("transfer", owner.getUniqueId().toString(), "confirm"), () -> {
                                try {
                                    check(plugin.getApi().getPlot(id).getOwner().equals(owner.getUniqueId()), "command workflow transfers back across regions");
                                    plugin.onReload(error -> {
                                        try {
                                            check(error == null, "Folia asynchronous reload");
                                            check(plugin.getApi().getPlot(id).getOwner().equals(owner.getUniqueId()), "Folia reload preserves ownership");
                                            checks.add("SERVER " + getServer().getVersion());
                                            finish(null);
                                        } catch (Throwable problem) { finish(problem); }
                                        return Unit.INSTANCE;
                                    });
                                } catch (Throwable error) { finish(error); }
                                return Unit.INSTANCE;
                            }), "transfer command accepted");
                    } catch (Throwable error) { finish(error); }
                }, () -> finish(new AssertionError("recipient retired")));
            });
        } catch (Throwable error) { finish(error); }
    }
    private void finish(Throwable error) {
        if (!finished.compareAndSet(false, true)) return;
        if (error == null) checks.add("RESULT PASS");
        else { checks.add("RESULT FAIL " + error); error.printStackTrace(); }
        try { Files.write(getDataFolder().toPath().resolve("result.txt"), checks); }
        catch (Exception failure) { failure.printStackTrace(); }
        getServer().getGlobalRegionScheduler().execute(this, () -> getServer().shutdown());
    }
}
