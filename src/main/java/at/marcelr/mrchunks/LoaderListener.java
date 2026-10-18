package at.marcelr.mrchunks;

import at.marcelr.mrchunks.gui.LoaderMenu;
import org.bukkit.Bukkit;
import org.bukkit.World;
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
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.event.world.WorldLoadEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
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
        } else if (AdminActions.isExempt(player)) {
            denial = "exempt";
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
            Loader conflict = overlapConflict(player, event.getBlock());
            if (conflict != null) {
                event.setCancelled(true);
                boolean own = conflict.owner().equals(player.getUniqueId());
                messages.send(player, own ? "place-overlap-denied-own" : "place-overlap-denied", Texts.loader(plugin, conflict));
                return;
            }
        }
        if (denial != null) {
            event.setCancelled(true);
            messages.send(player, denial);
        }
    }

    /** With loaders.overlap deny-own / deny-all: the first loader the new area would overlap, otherwise null. */
    private Loader overlapConflict(Player player, Block block) {
        Settings.OverlapMode mode = plugin.settings().loaders().overlap();
        if (mode != Settings.OverlapMode.DENY_OWN && mode != Settings.OverlapMode.DENY_ALL) {
            return null;
        }
        for (Loader other : loaders().overlapping(block.getWorld(), block.getX() >> 4, block.getZ() >> 4, null)) {
            if (mode == Settings.OverlapMode.DENY_ALL || other.owner().equals(player.getUniqueId())) {
                return other;
            }
        }
        return null;
    }

    /** With loaders.overlap warn: tells the player which loaders the new one overlaps and shows its border. */
    private void warnOverlap(Player player, Loader loader) {
        if (plugin.settings().loaders().overlap() != Settings.OverlapMode.WARN) {
            return;
        }
        World world = loader.world();
        List<Loader> others = world == null ? List.of() : loaders().overlapping(world, loader.chunkX(), loader.chunkZ(), loader);
        if (others.isEmpty()) {
            return;
        }
        Messages messages = plugin.messages();
        List<String> names = new ArrayList<>();
        for (Loader other : others) {
            names.add(messages.plain(other.owner().equals(player.getUniqueId()) ? "overlap-entry-own" : "overlap-entry-other",
                    "name", Texts.name(plugin, other), "player", LoaderManager.name(other.owner())));
        }
        messages.send(player, "place-overlap-warning", Texts.loader(plugin, loader),
                tag("loaders", String.join(", ", names)),
                tag("shared", loaders().sharedChunks(loader.chunkX(), loader.chunkZ(), others)));
        plugin.border().show(player, loader);
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
        warnOverlap(player, loader);
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
        if (!plugin.adminActions().canView(player, loader)) {
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

    /**
     * A chunk with loader blocks was loaded: check them right away (/fill, /setblock or WorldEdit may have removed one
     * while nobody was there). Active loaders are always loaded and also checked periodically.
     */
    @EventHandler
    public void onChunkLoad(ChunkLoadEvent event) {
        List<Loader> inChunk = loaders().inChunk(event.getWorld(), event.getChunk().getX(), event.getChunk().getZ());
        if (!inChunk.isEmpty()) {
            // Next tick: blocks must not be changed while the chunk is still being loaded.
            Bukkit.getScheduler().runTask(plugin, () -> plugin.reportValidation(loaders().validate(inChunk)));
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        plugin.adminActions().enforceExempt(player);
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (player.isOnline()) {
                plugin.updateChecker().notifyIfOutdated(player);
            }
        }, 60L);
    }
}
