package at.marcelr.mrchunks;

import at.marcelr.mrchunks.dummy.Dummies;
import at.marcelr.mrchunks.gui.LoaderMenu;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
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
import org.bukkit.event.entity.CreatureSpawnEvent;
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
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

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
            LoaderManager.Ban ban = loaders().groupBan(player.getUniqueId());
            if (ban != null) {
                event.setCancelled(true);
                messages.send(player, "banned", Messages.lazy("duration", () -> Texts.banDuration(plugin, ban)), tag("reason", ban.reason()));
                return;
            }
            int max = loaders().maxLoaders();
            if (loaders().count(AdminActions.accountOf(player)) >= max) {
                event.setCancelled(true);
                messages.send(player, "place-limit", tag("limit", max));
                return;
            }
            Loader conflict = overlapConflict(player, event.getBlock());
            if (conflict != null) {
                event.setCancelled(true);
                boolean own = conflict.owner().equals(AdminActions.accountOf(player));
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
        int r = placementReach(player);
        int cx = block.getX() >> 4;
        int cz = block.getZ() >> 4;
        for (Loader other : loaders().overlapping(block.getWorld().getUID(), cx - r, cz - r, cx + r, cz + r, null)) {
            if (mode == Settings.OverlapMode.DENY_ALL || other.owner().equals(AdminActions.accountOf(player))) {
                return other;
            }
        }
        return null;
    }

    /**
     * Size of a new loader (reach in every direction): loaders.default-reach, or the largest square that still fits
     * into the player's free budget. If not even 1x1 fits, the default - the loader is then placed switched off.
     */
    private int placementReach(Player player) {
        int reach = plugin.settings().loaders().defaultReach();
        long free = loaders().freeBudget(player);
        for (int r = reach; r >= 0; r--) {
            if ((2L * r + 1) * (2L * r + 1) <= free) {
                return r;
            }
        }
        return reach;
    }

    /** With loaders.overlap warn: tells the player which loaders the new one overlaps and shows its border. */
    private void warnOverlap(Player player, Loader loader) {
        plugin.messages().run(player, () -> warnOverlapNow(player, loader));
    }

    private void warnOverlapNow(Player player, Loader loader) {
        if (plugin.settings().loaders().overlap() != Settings.OverlapMode.WARN) {
            return;
        }
        World world = loader.world();
        List<Loader> others = world == null ? List.of() : loaders().overlapping(loader);
        if (others.isEmpty()) {
            return;
        }
        Messages messages = plugin.messages();
        List<String> names = new ArrayList<>();
        for (Loader other : others) {
            names.add(messages.plain(other.owner().equals(loader.owner()) ? "overlap-entry-own" : "overlap-entry-other",
                    "name", Texts.name(plugin, other), "player", LoaderManager.name(other.owner())));
        }
        messages.send(player, "place-overlap-warning", Texts.loader(plugin, loader),
                tag("loaders", String.join(", ", names)),
                tag("shared", loaders().sharedChunks(loader, others)));
        plugin.border().show(player, loader);
    }

    /** Registers the loader once the placement is final (no other plugin cancelled it). */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlaced(BlockPlaceEvent event) {
        if (!plugin.loaderItem().is(event.getItemInHand())) {
            return;
        }
        Player player = event.getPlayer();
        // Starts active (as large as the budget allows, up to the default size); with no budget left it is placed
        // switched off. A linked second account places for its main account.
        UUID owner = AdminActions.accountOf(player);
        int reach = placementReach(player);
        int area = (2 * reach + 1) * (2 * reach + 1);
        boolean active = loaders().fitsBudget(player, area);
        Loader loader = loaders().create(owner, event.getBlockPlaced(), reach, active);
        boolean smaller = active && reach < plugin.settings().loaders().defaultReach();
        plugin.messages().send(player, !active ? "placed-inactive" : smaller ? "placed-smaller" : "placed",
                Texts.loader(plugin, loader),
                tag("placed", loaders().count(owner)),
                tag("limit", loaders().maxLoaders()),
                plugin.adminActions().budgetTags(player));
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

    /**
     * No mobs on the loader block: spawns the world itself makes (natural, chunk generation, spawners, patrols, raids,
     * portals, reinforcements ...) are cancelled when they stand on it. What a player does on purpose (spawn eggs,
     * commands, breeding, building golems, buckets) stays allowed.
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onSpawn(CreatureSpawnEvent event) {
        if (WORLD_SPAWNS.contains(event.getSpawnReason())
                && loaders().isLoaderBlock(event.getLocation().getBlock().getRelative(BlockFace.DOWN))) {
            event.setCancelled(true);
        }
    }

    private static final Set<CreatureSpawnEvent.SpawnReason> WORLD_SPAWNS = EnumSet.of(
            CreatureSpawnEvent.SpawnReason.NATURAL,
            CreatureSpawnEvent.SpawnReason.JOCKEY, CreatureSpawnEvent.SpawnReason.SPAWNER,
            CreatureSpawnEvent.SpawnReason.TRIAL_SPAWNER, CreatureSpawnEvent.SpawnReason.OMINOUS_ITEM_SPAWNER,
            CreatureSpawnEvent.SpawnReason.PATROL, CreatureSpawnEvent.SpawnReason.RAID,
            CreatureSpawnEvent.SpawnReason.REINFORCEMENTS, CreatureSpawnEvent.SpawnReason.VILLAGE_DEFENSE,
            CreatureSpawnEvent.SpawnReason.VILLAGE_INVASION, CreatureSpawnEvent.SpawnReason.NETHER_PORTAL,
            CreatureSpawnEvent.SpawnReason.TRAP, CreatureSpawnEvent.SpawnReason.LIGHTNING,
            CreatureSpawnEvent.SpawnReason.SILVERFISH_BLOCK, CreatureSpawnEvent.SpawnReason.ENDER_PEARL,
            CreatureSpawnEvent.SpawnReason.DROWNED, CreatureSpawnEvent.SpawnReason.BEEHIVE);

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
        plugin.messages().run(player, () -> new LoaderMenu(plugin, player, loader.id(), (Runnable) null).open());
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
        if (Dummies.isDummy(player)) {
            return;
        }
        plugin.adminActions().enforceExempt(player);
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (player.isOnline()) {
                plugin.updateChecker().notifyIfOutdated(player);
            }
        }, 60L);
    }
}
