package at.marcelr.mrchunks.lag.nms;

import at.marcelr.mrchunks.lag.ItemActivation;
import net.minecraft.server.MinecraftServer;
import org.bukkit.craftbukkit.entity.CraftEntity;
import org.bukkit.entity.Entity;

/**
 * Keeps an entity fully ticked through Paper's activation range: Paper ticks an entity outside the range of every
 * player only now and then (items every 4th tick) unless its {@code activatedTick} lies in the future - which is what
 * Paper itself does for entities moved by pistons. Written against Paper 26.3; MRchunks loads this class by name and
 * switches the feature off if it does not fit the running server.
 */
public final class NmsItemActivation implements ItemActivation.Backend {

    public NmsItemActivation() throws ReflectiveOperationException {
        // Touch what keepActive needs, so a changed server fails here (feature off) and not on every tick.
        net.minecraft.world.entity.Entity.class.getField("activatedTick");
        MinecraftServer.class.getField("currentTick");
        CraftEntity.class.getName();
    }

    @Override
    public void keepActive(Entity entity, int ticks) {
        net.minecraft.world.entity.Entity handle = ((CraftEntity) entity).getHandle();
        handle.activatedTick = Math.max(handle.activatedTick, MinecraftServer.currentTick + ticks);
    }
}
