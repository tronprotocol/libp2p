package org.tron.p2p.connection;

import java.lang.reflect.Field;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.List;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.tron.p2p.P2pConfig;
import org.tron.p2p.P2pEventHandler;
import org.tron.p2p.base.Parameter;
import org.tron.p2p.connection.business.pool.ConnPoolService;

/**
 * A remote host supporting TCP port reuse can bind an outgoing socket to its listening port.
 * Inbound and outbound connections can then share the remote address seen by this node.
 * If NAT is involved, its port mappings must also preserve that address equality.
 * These tests model those channels directly without requiring platform-specific socket options.
 */
public class ConnPoolServiceLifecycleTest {

  private static final InetSocketAddress REMOTE = new InetSocketAddress("127.0.0.2", 18888);
  private static P2pConfig previousConfig;
  private List<P2pEventHandler> previousHandlers;
  private final List<Channel> channels = new ArrayList<>();
  private ConnPoolService pool;

  @BeforeClass
  public static void initConfig() {
    previousConfig = Parameter.p2pConfig;
    Parameter.p2pConfig = new P2pConfig();
  }

  @AfterClass
  public static void restoreConfig() {
    Parameter.p2pConfig = previousConfig;
  }

  @Before
  public void init() {
    previousHandlers = Parameter.handlerList;
    Parameter.handlerList = new ArrayList<>();
    pool = new ConnPoolService();
  }

  @After
  public void destroy() {
    channels.forEach(channel -> channel.setDisconnect(true));
    pool.close();
    Parameter.handlerList = previousHandlers;
  }

  @Test
  public void sameAddressPeersAreTrackedSeparatelyInEitherCloseOrder() throws Exception {
    for (boolean firstActive : new boolean[]{false, true}) {
      for (boolean closeFirst : new boolean[]{false, true}) {
        Channel first = channel(firstActive);
        Channel second = channel(!firstActive);
        Assert.assertNotSame(first, second);
        Assert.assertEquals(first, second);
        pool.onConnect(first);
        pool.onConnect(second);
        assertCounts(1, 1);
        assertPeers(first, second);

        pool.onDisconnect(closeFirst ? first : second);
        Channel survivor = closeFirst ? second : first;
        assertCounts(survivor.isActive() ? 1 : 0, survivor.isActive() ? 0 : 1);
        assertPeers(survivor);
        pool.onDisconnect(survivor);
        assertCounts(0, 0);
        assertPeers();
      }
    }
  }

  @Test
  public void unregisteredSameAddressDisconnectDoesNotRemoveRegisteredPeer() throws Exception {
    for (boolean active : new boolean[]{false, true}) {
      Channel registered = channel(active);
      Channel unregistered = channel(!active);
      pool.onConnect(registered);
      Assert.assertFalse(unregistered.isFinishHandshake());

      pool.onDisconnect(unregistered);
      pool.onDisconnect(unregistered);
      assertCounts(active ? 1 : 0, active ? 0 : 1);
      assertPeers(registered);
      pool.onDisconnect(registered);
      assertCounts(0, 0);
      assertPeers();
    }
  }

  @Test
  public void duplicateCallbacksCountEachInstanceOnce() throws Exception {
    Channel inbound = channel(false);
    Channel outbound = channel(true);
    pool.onConnect(inbound);
    pool.onConnect(inbound);
    pool.onConnect(outbound);
    pool.onConnect(outbound);
    assertCounts(1, 1);
    assertPeers(inbound, outbound);

    pool.onDisconnect(inbound);
    pool.onDisconnect(inbound);
    assertCounts(1, 0);
    assertPeers(outbound);
    pool.onDisconnect(outbound);
    pool.onDisconnect(outbound);
    assertCounts(0, 0);
    assertPeers();
  }

  @Test
  public void lateDisconnectDoesNotRemoveReplacementAtSameAddress() throws Exception {
    for (boolean active : new boolean[]{false, true}) {
      Channel old = channel(active);
      pool.onConnect(old);
      pool.onDisconnect(old);
      Channel replacement = channel(!active);
      pool.onConnect(replacement);

      pool.onDisconnect(old);
      assertCounts(active ? 0 : 1, active ? 1 : 0);
      assertPeers(replacement);
      pool.onDisconnect(replacement);
      assertCounts(0, 0);
      assertPeers();
    }
  }

  @Test
  public void repeatedSameAddressLifecyclesDoNotAccumulateCounterDrift() throws Exception {
    for (int i = 0; i < 100; i++) {
      Channel first = channel(i % 2 == 0);
      Channel second = channel(!first.isActive());
      pool.onConnect(first);
      pool.onConnect(second);
      pool.onDisconnect(second);
      pool.onDisconnect(first);
      assertCounts(0, 0);
      assertPeers();
    }
  }

  private Channel channel(boolean active) throws Exception {
    Channel channel = new Channel();
    set(channel, "inetSocketAddress", REMOTE);
    set(channel, "inetAddress", REMOTE.getAddress());
    set(channel, "isActive", active);
    channels.add(channel);
    return channel;
  }

  private void assertCounts(int active, int passive) {
    Assert.assertEquals(active, pool.getActivePeersCount().get());
    Assert.assertEquals(passive, pool.getPassivePeersCount().get());
  }

  @SuppressWarnings("unchecked")
  private void assertPeers(Channel... expected) throws Exception {
    Field field = ConnPoolService.class.getDeclaredField("activePeers");
    field.setAccessible(true);
    List<Channel> actual = (List<Channel>) field.get(pool);
    Assert.assertEquals(expected.length, actual.size());
    for (int i = 0; i < expected.length; i++) {
      Assert.assertSame(expected[i], actual.get(i));
    }
  }

  private void set(Channel channel, String name, Object value) throws Exception {
    Field field = Channel.class.getDeclaredField(name);
    field.setAccessible(true);
    field.set(channel, value);
  }
}
