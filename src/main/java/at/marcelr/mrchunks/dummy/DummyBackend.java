package at.marcelr.mrchunks.dummy;

import org.bukkit.World;
import org.bukkit.entity.Player;

import java.util.UUID;
import java.util.function.Consumer;

/**
 * Logs dummy players in and out. The only part of MRchunks that uses server internals (see the nms package); it is
 * loaded on demand, so a server version it does not fit only switches the dummies off.
 */
public interface DummyBackend {

    /**
     * Logs a dummy in at the given position, like a joining player (PlayerJoinEvent included).
     * @param skinValue     "textures" property of the skin, or null for the default skin
     * @param beforeJoin    gets the Bukkit player before the join event fires (to mark it as a dummy)
     */
    Player spawn(World world, double x, double y, double z, float yaw, UUID id, String name,
                 String skinValue, String skinSignature, Consumer<Player> beforeJoin);

    /** Logs the dummy out again (PlayerQuitEvent included). Its data is not saved. */
    void remove(Player dummy);
}
