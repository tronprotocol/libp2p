package org.tron.p2p;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.tron.p2p.base.Parameter;
import org.tron.p2p.connection.ChannelManager;
import org.tron.p2p.connection.ConnectionPolicy;

public class P2pServicePeerManagementTest {

  private P2pService p2pService;

  @Before
  public void setUp() {
    Parameter.p2pConfig = new P2pConfig();
    p2pService = new P2pService();
    ConnectionPolicy.replaceBlockedIps(Collections.emptySet());
  }

  @After
  public void tearDown() {
    Parameter.p2pConfig.getActiveNodes().clear();
    ConnectionPolicy.replaceBlockedIps(Collections.emptySet());
  }

  @Test
  public void blockedActiveNodeIsRejected() {
    InetSocketAddress address = new InetSocketAddress("192.0.2.20", 18888);
    ConnectionPolicy.replaceBlockedIps(Collections.singleton(address.getAddress()));

    Assert.assertFalse(p2pService.addActiveNode(address));
    Assert.assertFalse(Parameter.p2pConfig.getActiveNodes().contains(address));
  }

  @Test
  public void removeAndDisconnectDoNotChangeEachOthersState() {
    InetSocketAddress address = new InetSocketAddress("192.0.2.21", 18888);
    Assert.assertTrue(p2pService.addActiveNode(address));
    Assert.assertFalse(p2pService.addActiveNode(address));

    Assert.assertEquals(0, p2pService.disconnect(address));
    Assert.assertTrue(Parameter.p2pConfig.getActiveNodes().contains(address));
    Assert.assertTrue(p2pService.removeActiveNode(address));
    Assert.assertFalse(p2pService.removeActiveNode(address));
  }

  @Test
  public void invalidAddressIsRejectedBeforeNetworkAccess() {
    try {
      p2pService.addActiveNode(InetSocketAddress.createUnresolved("peer.example", 18888));
      Assert.fail("Expected unresolved address to be rejected");
    } catch (IllegalArgumentException expected) {
      Assert.assertTrue(expected.getMessage().contains("must be resolved"));
    }
  }

  @Test
  public void activeNodeAddressPolicyAllowsLoopback() {
    Assert.assertTrue(p2pService.addActiveNode(new InetSocketAddress("127.0.0.1", 18888)));
    Assert.assertTrue(p2pService.addActiveNode(new InetSocketAddress("::1", 18888)));
  }

  @Test
  public void activeNodeAddressPolicyRejectsNonDialableAddresses() {
    String[] invalidAddresses = {
        "0.0.0.0", "::", "224.0.0.1", "ff02::1", "255.255.255.255"
    };
    for (String invalidAddress : invalidAddresses) {
      try {
        p2pService.addActiveNode(new InetSocketAddress(invalidAddress, 18888));
        Assert.fail("Expected address to be rejected: " + invalidAddress);
      } catch (IllegalArgumentException expected) {
        Assert.assertTrue(expected.getMessage().contains("must not use"));
      }
    }
  }

  @Test
  public void activeNodeSetterKeepsCollectionSafeForRuntimeUpdates() {
    P2pConfig config = new P2pConfig();
    config.setActiveNodes(new ArrayList<InetSocketAddress>());

    java.util.Iterator<InetSocketAddress> iterator = config.getActiveNodes().iterator();
    config.getActiveNodes().add(new InetSocketAddress("192.0.2.22", 18888));

    Assert.assertFalse(iterator.hasNext());
    Assert.assertEquals(1, config.getActiveNodes().size());
  }

  @Test
  public void replaceBlockedIpsUpdatesP2pConfigWithDefensiveCopy() {
    InetAddress address = new InetSocketAddress("192.0.2.23", 18888).getAddress();
    Set<InetAddress> blockedIps = new HashSet<>();
    blockedIps.add(address);

    p2pService.replaceBlockedIps(blockedIps);

    Assert.assertEquals(blockedIps, Parameter.p2pConfig.getBlockedIps());
    Assert.assertTrue(ConnectionPolicy.isBlocked(address));
    blockedIps.clear();
    Assert.assertEquals(Collections.singleton(address), Parameter.p2pConfig.getBlockedIps());
  }
}
