package at.marcelr.mrchunks.gui;

import com.destroystokyo.paper.profile.PlayerProfile;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Player heads for the menus without asking Mojang again and again. Menus are drawn again every second; a head whose
 * profile has no skin yet makes the server look it up each time - for players who are offline (leaderboard, games)
 * that quickly runs into Mojang's rate limit (HTTP 429). So: a profile that already has its skin is used as it is,
 * any other is completed once in the background and kept for 10 minutes - a failed lookup too (plain head meanwhile).
 */
final class Heads {

    private static final long KEEP_MILLIS = 10 * 60 * 1000L;

    /** A completed profile (or null = no skin found) and when it was looked up. */
    private record Entry(PlayerProfile profile, long time) {
    }

    private static final Map<UUID, Entry> CACHE = new ConcurrentHashMap<>();
    private static final Map<UUID, Boolean> LOADING = new ConcurrentHashMap<>();

    private Heads() {
    }

    static ItemStack of(OfflinePlayer owner) {
        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        PlayerProfile profile = profile(owner);
        if (profile != null) {
            head.editMeta(SkullMeta.class, meta -> meta.setPlayerProfile(profile));
        }
        return head;
    }

    /** The profile to show, or null for a plain head (no skin known yet / none). */
    private static PlayerProfile profile(OfflinePlayer owner) {
        Player online = owner.getPlayer();
        if (online != null && online.getPlayerProfile().hasTextures()) {
            return online.getPlayerProfile();
        }
        UUID id = owner.getUniqueId();
        Entry entry = CACHE.get(id);
        long now = System.currentTimeMillis();
        if (entry != null && now - entry.time() < KEEP_MILLIS) {
            return entry.profile();
        }
        if (LOADING.putIfAbsent(id, Boolean.TRUE) == null) {
            PlayerProfile lookup = Bukkit.createProfile(id, owner.getName());
            lookup.update().whenComplete((done, error) -> {
                CACHE.put(id, new Entry(error == null && done != null && done.hasTextures() ? done : null,
                        System.currentTimeMillis()));
                LOADING.remove(id);
            });
        }
        // While it loads (or after a failure): the last known one, or a plain head.
        return entry == null ? null : entry.profile();
    }
}
