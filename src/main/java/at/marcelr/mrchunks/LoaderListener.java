package at.marcelr.mrchunks;

import at.marcelr.mrchunks.gui.LoaderMenu;
import org.bukkit.Bukkit;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockBurnEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.world.WorldLoadEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

import java.util.List;

import static at.marcelr.mrchunks.Messages.tag;

public final class LoaderListener implements Listener {

    private final MRChunks plugin;

    public LoaderListener(MRChunks plugin) {
        this.plugin = plugin;
    }

    private LoaderManager loaders() {
        return plugin.loaders();
    }

    // -------------------------------------------------------------- placing

    /** Checks whether the player may place a loader here. Runs before protection plugins have the final say. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlaceCheck(BlockPlaceEvent event) {
        if (!plugin.loaderItem().is(event.getItemInHand())) {
            return;
        }
        Player player = event.getPlayer();
        Messages messages = plugin.messages();
        String denial = null;
        if (!player.hasPermission(AdminActions.PERM_USE)) {
            denial = "no-permission";
        } else if (!loaders().isWorldAllowed(event.getBlock().getWorld())) {
            denial = "place-world-disabled";
        } else {
            LoaderManager.Ban ban = loaders().ban(player.getUniqueId());
            if (ban != null) {
                event.setCancelled(true);
                messages.send(player, "banned", tag("duration", Texts.banDuration(plugin, ban)), tag("reason", ban.reason()));
                return;
            }
            LoaderManager.Limit limit = loaders().limit(player);
            if (loaders().count(player.getUniqueId()) >= limit.value()) {
                event.setCancelled(true);
                messages.send(player, "place-limit", tag("limit", Texts.limit(limit)));
                return;
            }
        }
        if (denial != null) {
            event.setCancelled(true);
            messages.send(player, denial);
        }
    }

    /** Registers the loader once the placement is final (no other plugin cancelled it). */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlaced(BlockPlaceEvent event) {
        if (!plugin.loaderItem().is(event.getItemInHand())) {
            return;
        }
        Player player = event.getPlayer();
        // Starts active only if the player still has an active slot; otherwise it is placed switched off.
        boolean active = loaders().canActivate(player);
        Loader loader = loaders().create(player.getUniqueId(), event.getBlockPlaced(), active);
        plugin.messages().send(player, active ? "placed" : "placed-inactive", Texts.loader(plugin, loader),
                tag("placed", loaders().count(player.getUniqueId())),
                tag("limit", Texts.limit(loaders().limit(player))),
                tag("active", loaders().activeCount(player.getUniqueId())),
                tag("active-limit", Texts.limit(loaders().activeLimit(player))));
        plugin.discord().log("loader-placed", plugin.adminActions().discordTags(loader));
    }

    /** A loader item is an emerald block - never let it be crafted into emeralds. */
    @EventHandler
    public void onCraft(PrepareItemCraftEvent event) {
        for (ItemStack ingredient : event.getInventory().getMatrix()) {
            if (plugin.loaderItem().is(ingredient)) {
                event.getInventory().setResult(null);
                return;
            }
        }
    }

    // ------------------------------------------------------- indestructible

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        if (loaders().isLoaderBlock(event.getBlock())) {
            event.setCancelled(true);
            plugin.messages().send(event.getPlayer(), "break-denied");
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent event) {
        event.blockList().removeIf(loaders()::isLoaderBlock);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent event) {
        event.blockList().removeIf(loaders()::isLoaderBlock);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPistonExtend(BlockPistonExtendEvent event) {
        if (anyLoader(event.getBlocks())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPistonRetract(BlockPistonRetractEvent event) {
        if (anyLoader(event.getBlocks())) {
            event.setCancelled(true);
        }
    }

    /** Withers, endermen, falling blocks, ... */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityChangeBlock(EntityChangeBlockEvent event) {
        if (loaders().isLoaderBlock(event.getBlock())) {
            event.setCancelled(true);
        }
    }

    /** Only relevant if item.material is a flammable block. */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBurn(BlockBurnEvent event) {
        if (loaders().isLoaderBlock(event.getBlock())) {
            event.setCancelled(true);
        }
    }

    private boolean anyLoader(List<Block> blocks) {
        for (Block block : blocks) {
            if (loaders().isLoaderBlock(block)) {
                return true;
            }
        }
        return false;
    }

    // ---------------------------------------------------------------- menu

    /** Right-click opens the loader's menu; sneaking keeps normal building against the block possible. */
    @EventHandler(priority = EventPriority.HIGH)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || event.getHand() != EquipmentSlot.HAND
                || event.getClickedBlock() == null || event.getPlayer().isSneaking()) {
            return;
        }
        Loader loader = loaders().at(event.getClickedBlock());
        if (loader == null) {
            return;
        }
        event.setCancelled(true);
        Player player = event.getPlayer();
        if (!AdminActions.canView(player, loader)) {
            plugin.messages().send(player, "no-permission");
            return;
        }
        new LoaderMenu(plugin, player, loader.id(), null).open();
        if (plugin.settings().border().showOnOpen()) {
            plugin.border().show(player, loader);
        }
    }

    // -------------------------------------------------------------- worlds

    @EventHandler
    public void onWorldLoad(WorldLoadEvent event) {
        loaders().applyWorld(event.getWorld());
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (player.isOnline()) {
                plugin.updateChecker().notifyIfOutdated(player);
            }
        }, 60L);
    }
}
