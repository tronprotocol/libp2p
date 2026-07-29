package org.tron.p2p.connection;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import lombok.extern.slf4j.Slf4j;
import org.tron.p2p.base.Parameter;
import org.tron.p2p.connection.business.handshake.DisconnectCode;

/**
 * Admission control for inbound connections that have NOT yet finished the handshake.
 *
 * <p>Root problem it fixes: the connection caps (maxConnections /
 * maxConnectionsWithSameIp) and the ban check all live in {@link ChannelManager#processPeer},
 * which is only reached after a full frame decodes. A connection that never completes a
 * frame is therefore never counted, so a single source IP can open an unbounded number of
 * un-handshaked connections and hold them open via a slow trickle (slowloris).
 *
 * <p>Design (fully event-driven, no polling): the map of live pending channels IS the source
 * of truth; its size is the global count and the per-IP count is derived by filtering it.
 * Lifecycle is guaranteed by Netty:
 * <ul>
 *   <li>{@code channelActive} fires exactly once when a connection is established
 *       -&gt; {@link #register};</li>
 *   <li>the channel's {@code closeFuture} fires exactly once when it closes,
 *       and the handshake completes at a well-defined point
 *       -&gt; {@link #release}.</li>
 * </ul>
 * So no counter can leak and no background sweeper is needed. A connection that neither
 * finishes the handshake nor closes (slow trickle) is evicted by the per-read handshake
 * deadline in {@code P2pProtobufVarint32FrameDecoder}, and a fully idle one by the existing
 * 60s ReadTimeoutHandler — both then fire closeFuture and release the slot.
 *
 * <p>Established (handshaked) connections are NOT managed here — they remain owned by
 * {@link ChannelManager#channels} / processPeer, unchanged.
 */
@Slf4j(topic = "net")
public class InboundAdmission {

  // key = remote socket address (unique per TCP connection)
  private final Map<InetSocketAddress, Channel> pending = new ConcurrentHashMap<>();

  /**
   * Called at channelActive for an inbound, non-discovery, non-trust connection, before any
   * bytes are read. Enforces the global and per-IP pending caps. Returns
   * {@link DisconnectCode#NORMAL} if admitted.
   */
  public synchronized DisconnectCode register(Channel channel) {
    log.debug("register channel {}, size: {}",
            channel.getInetSocketAddress(), pending.size());
    if (pending.size() >= Parameter.MAX_PENDING_CONNECTIONS) {
      return DisconnectCode.TOO_MANY_PEERS;
    }

    // Per-IP pending cap tracks the configured established per-IP limit, with 2x headroom for
    // reconnection bursts. Scales automatically if the operator raises maxConnectionsWithSameIp.
    InetAddress ip = channel.getInetAddress();
    long sameIp = pending.values().stream()
        .filter(c -> ip != null && ip.equals(c.getInetAddress()))
        .count();
    if (sameIp >= 2L * Parameter.p2pConfig.getMaxConnectionsWithSameIp()) {
      return DisconnectCode.MAX_CONNECTION_WITH_SAME_IP;
    }

    pending.put(channel.getInetSocketAddress(), channel);
    log.debug("register channel {} success, size: {}",
            channel.getInetSocketAddress(), pending.size());
    return DisconnectCode.NORMAL;
  }

  /**
   * Idempotent removal from the pending pool. Called both when the channel closes (via
   * closeFuture) and when the handshake completes (graduation) — whichever happens first.
   */
  public void release(Channel channel) {
    log.debug("release channel {}, size: {}",
            channel.getInetSocketAddress(), pending.size());
    if (channel.getInetSocketAddress() != null) {
      pending.remove(channel.getInetSocketAddress());
    }
    log.debug("release channel {} success, size: {}",
            channel.getInetSocketAddress(), pending.size());
  }

  public int pendingSize() {
    return pending.size();
  }
}
