package org.tron.p2p.connection;

import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import java.lang.reflect.Field;
import java.net.InetSocketAddress;
import org.junit.Assert;
import org.junit.Test;
import org.tron.p2p.P2pConfig;
import org.tron.p2p.base.Parameter;
import org.tron.p2p.connection.business.handshake.DisconnectCode;
import org.tron.p2p.connection.socket.P2pProtobufVarint32FrameDecoder;

public class InboundAdmissionTest {

  private static void setField(Object o, String name, Object v) throws Exception {
    Field f = Channel.class.getDeclaredField(name);
    f.setAccessible(true);
    f.set(o, v);
  }

  /** An inbound Channel with the given remote address (isActive=false, not handshaked). */
  private static Channel makeChannel(String ip, int port) throws Exception {
    Channel c = new Channel();
    InetSocketAddress addr = new InetSocketAddress(ip, port);
    setField(c, "inetSocketAddress", addr);
    setField(c, "inetAddress", addr.getAddress());
    return c;
  }

  /** Single IP cannot exceed the per-IP pending cap — this is what blocks the slowloris. */
  @Test
  public void testPerIpPendingCap() throws Exception {
    Parameter.p2pConfig = new P2pConfig();
    InboundAdmission admission = new InboundAdmission();

    int cap = 2 * Parameter.p2pConfig.getMaxConnectionsWithSameIp(); // default 2 -> 4
    for (int i = 0; i < cap; i++) {
      Assert.assertEquals(DisconnectCode.NORMAL,
          admission.register(makeChannel("1.1.1.1", 10000 + i)));
    }
    Assert.assertEquals(cap, admission.pendingSize());
    // one more from the same IP must be rejected
    Assert.assertEquals(DisconnectCode.MAX_CONNECTION_WITH_SAME_IP,
        admission.register(makeChannel("1.1.1.1", 20000)));
    // a different IP is still admitted
    Assert.assertEquals(DisconnectCode.NORMAL,
        admission.register(makeChannel("2.2.2.2", 10000)));
  }

  /** Completing the handshake frees the pending slot so legit peers keep reconnecting. */
  @Test
  public void testGraduateReleasesSlot() throws Exception {
    Parameter.p2pConfig = new P2pConfig();
    InboundAdmission admission = new InboundAdmission();

    Channel c = makeChannel("3.3.3.3", 10000);
    Assert.assertEquals(DisconnectCode.NORMAL, admission.register(c));
    Assert.assertEquals(1, admission.pendingSize());

    c.setFinishHandshake(true);
    admission.release(c); // called at the setFinishHandshake site in production
    Assert.assertEquals(0, admission.pendingSize());
  }

  /** release is idempotent — safe to fire from both the closeFuture and graduation paths. */
  @Test
  public void testReleaseIsIdempotent() throws Exception {
    Parameter.p2pConfig = new P2pConfig();
    InboundAdmission admission = new InboundAdmission();

    Channel c = makeChannel("4.4.4.4", 10000);
    admission.register(c);
    admission.release(c);
    admission.release(c); // second release (e.g. closeFuture after graduation) must be a no-op
    Assert.assertEquals(0, admission.pendingSize());
  }

  /**
   * A connection that never finishes the handshake is evicted by the per-read deadline check
   * in the frame decoder — driven by the attacker's own keep-alive byte, no timer needed.
   */
  @Test
  public void testHandshakeDeadlineEvictionInDecoder() throws Exception {
    Parameter.p2pConfig = new P2pConfig();
    Channel c = makeChannel("5.5.5.5", 10000);
    // inbound, un-handshaked, aged past the handshake deadline
    setField(c, "startTime", System.currentTimeMillis() - Parameter.HANDSHAKE_TIMEOUT_MS - 1000);

    EmbeddedChannel ec = new EmbeddedChannel(new P2pProtobufVarint32FrameDecoder(c));
    setField(c, "ctx", ec.pipeline().firstContext());

    // one trickle byte drives decode(), which evicts the timed-out connection
    ec.writeInbound(Unpooled.wrappedBuffer(new byte[] {0x05}));

    Assert.assertTrue("timed-out pending connection must be closed", c.isDisconnect());
    Assert.assertFalse("underlying channel must be closed", ec.isActive());
  }

  /** Before the deadline, a trickle byte does NOT evict a legitimate mid-handshake connection. */
  @Test
  public void testFreshConnectionNotEvicted() throws Exception {
    Parameter.p2pConfig = new P2pConfig();
    Channel c = makeChannel("6.6.6.6", 10000); // startTime = now, within deadline

    EmbeddedChannel ec = new EmbeddedChannel(new P2pProtobufVarint32FrameDecoder(c));
    setField(c, "ctx", ec.pipeline().firstContext());

    ec.writeInbound(Unpooled.wrappedBuffer(new byte[] {0x05}));

    Assert.assertFalse("fresh connection must not be evicted", c.isDisconnect());
    Assert.assertTrue(ec.isActive());
  }
}
