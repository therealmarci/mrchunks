package at.marcelr.mrchunks.api;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.plugin.Plugin;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.logging.Logger;

/**
 * Asks MRpvp whether a world is its arena world ({@code at.marcelr.mrpvp.api.MRpvpApi.isArenaWorld(String)}) - no
 * chunk loaders there. Found at runtime, no dependency in either direction; without MRpvp there is no arena world.
 * The method is looked up once per MRpvp instance, so a reload is picked up.
 */
public final class MRpvpArenaWorld {

    private static final String API_CLASS = "at.marcelr.mrpvp.api.MRpvpApi";

    private final Logger log;
    private Plugin resolvedFor;
    private MethodHandle isArenaWorld;

    public MRpvpArenaWorld(Logger log) {
        this.log = log;
    }

    public boolean is(World world) {
        Plugin mrpvp = Bukkit.getPluginManager().getPlugin("MRpvp");
        if (mrpvp == null || !mrpvp.isEnabled()) {
            return false;
        }
        if (mrpvp != resolvedFor) {
            resolvedFor = mrpvp;
            try {
                Class<?> api = Class.forName(API_CLASS, true, mrpvp.getClass().getClassLoader());
                isArenaWorld = MethodHandles.publicLookup().findStatic(api, "isArenaWorld",
                        MethodType.methodType(boolean.class, String.class));
            } catch (ReflectiveOperationException e) {
                isArenaWorld = null;
            }
        }
        if (isArenaWorld == null) {
            return false;
        }
        try {
            return (boolean) isArenaWorld.invokeExact(world.getName());
        } catch (Throwable e) {
            log.warning("MRpvp arena world check failed, ignoring it from now on: " + e);
            isArenaWorld = null;
            return false;
        }
    }
}
