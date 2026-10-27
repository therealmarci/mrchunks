package at.marcelr.mrchunks.dummy.nms;

import io.netty.channel.ChannelFutureListener;
import io.netty.channel.embedded.EmbeddedChannel;
import net.minecraft.network.Connection;
import net.minecraft.network.PacketListener;
import net.minecraft.network.ProtocolInfo;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import org.jspecify.annotations.Nullable;

import java.net.InetAddress;
import java.net.InetSocketAddress;

/**
 * Connection of a dummy player: there is no client, every packet is dropped. The channel is an open
 * {@link EmbeddedChannel} so the server treats the player as connected; the loopback address keeps plugins that read
 * {@code Player#getAddress()} from failing.
 */
final class FakeConnection extends Connection {

    FakeConnection() {
        super(PacketFlow.SERVERBOUND);
        this.channel = new EmbeddedChannel();
        this.address = new InetSocketAddress(InetAddress.getLoopbackAddress(), 0);
    }

    @Override
    public void send(final Packet<?> packet, final @Nullable ChannelFutureListener listener, final boolean flush) {
    }

    @Override
    public boolean isConnected() {
        return true;
    }

    @Override
    public <T extends PacketListener> void setupInboundProtocol(final ProtocolInfo<T> protocol, final T packetListener) {
    }

    @Override
    public void setupOutboundProtocol(final ProtocolInfo<?> protocol) {
    }

    @Override
    public void flushChannel() {
    }

    @Override
    public void setReadOnly() {
    }

    @Override
    public void handleDisconnection() {
    }
}
