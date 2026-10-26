package at.marcelr.mrchunks.dummy.nms;

import at.marcelr.mrchunks.dummy.DummyBackend;
import com.google.common.collect.ImmutableMultimap;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
import com.mojang.authlib.properties.PropertyMap;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ParticleStatus;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.ChatVisiblity;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.craftbukkit.CraftServer;
import org.bukkit.craftbukkit.CraftWorld;
import org.bukkit.craftbukkit.entity.CraftPlayer;
import org.bukkit.entity.Player;

import java.util.UUID;
import java.util.function.Consumer;

/**
 * Dummy players as real {@link ServerPlayer}s on a {@link FakeConnection}, logged in through
 * {@code PlayerList.placeNewPlayer} like a joining client. Written against Paper 26.3; MRchunks loads this class by
 * name and switches the dummies off if it does not fit the running server.
 */
public final class NmsDummyBackend implements DummyBackend {

    /** All skin layers (hat, jacket, sleeves, trousers). */
    private static final int ALL_SKIN_PARTS = 0x7F;

    public NmsDummyBackend() {
        // Touch what spawn/remove need, so a changed server fails here (MRchunks then disables dummies) and not later.
        server();
        ClientInformation.class.getName();
    }

    private static MinecraftServer server() {
        return ((CraftServer) Bukkit.getServer()).getServer();
    }

    @Override
    public Player spawn(World world, double x, double y, double z, float yaw, UUID id, String name,
                        String skinValue, String skinSignature, Consumer<Player> beforeJoin) {
        MinecraftServer server = server();
        ServerLevel level = ((CraftWorld) world).getHandle();
        PropertyMap properties = skinValue == null ? PropertyMap.EMPTY
                : new PropertyMap(ImmutableMultimap.of("textures", new Property("textures", skinValue, skinSignature)));
        GameProfile profile = new GameProfile(id, name, properties);
        // allowsListing false: not shown in the player sample of the server list.
        ClientInformation client = new ClientInformation("en_us", 2, ChatVisiblity.HIDDEN, false, ALL_SKIN_PARTS,
                HumanoidArm.RIGHT, false, false, ParticleStatus.MINIMAL);
        ServerPlayer player = new ServerPlayer(server, level, profile, client);
        player.snapTo(x, y, z, yaw, 0);
        beforeJoin.accept(player.getBukkitEntity());
        server.getPlayerList().placeNewPlayer(new FakeConnection(), player, CommonListenerCookie.createInitial(profile, false));
        return player.getBukkitEntity();
    }

    @Override
    public void remove(Player dummy) {
        ServerPlayer player = ((CraftPlayer) dummy).getHandle();
        // What ServerGamePacketListenerImpl does when a client disconnects.
        player.disconnect();
        server().getPlayerList().remove(player);
        player.getTextFilter().leave();
    }
}
