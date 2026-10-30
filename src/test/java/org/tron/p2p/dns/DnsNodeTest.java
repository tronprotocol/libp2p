package org.tron.p2p.dns;


import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.Assert;
import org.junit.Test;
import org.tron.p2p.dns.tree.Algorithm;
import org.tron.p2p.dns.tree.Entry;
import org.tron.p2p.dns.tree.NodesEntry;
import org.tron.p2p.protos.Discover.EndPoints;
import org.tron.p2p.protos.Discover.Endpoint;

public class DnsNodeTest {

  @Test
  public void unavailableIpv6ZoneDoesNotAbortTheNodesRecord() throws Exception {
    EndPoints endpoints = EndPoints.newBuilder()
        .addNodes(Endpoint.newBuilder()
            .setAddress(ByteString.copyFromUtf8("192.0.2.1"))
            .setAddressIpv6(ByteString.copyFromUtf8(
                "2001:db8::1%libp2p-missing-interface"))
            .setPort(18888))
        .addNodes(Endpoint.newBuilder()
            .setAddressIpv6(ByteString.copyFromUtf8("2001:DB8::2"))
            .setPort(18889))
        .build();
    String txt = Entry.nodesPrefix + Algorithm.encode64(endpoints.toByteArray());

    List<DnsNode> nodes = NodesEntry.parseEntry(txt).getNodes();

    Assert.assertEquals(2, nodes.size());
    Assert.assertEquals("192.0.2.1", nodes.get(0).getHostV4());
    Assert.assertNull(nodes.get(0).getHostV6());
    Assert.assertEquals(18888, nodes.get(0).getPort());
    Assert.assertEquals(new DnsNode(null, "192.0.2.1", null, 18888), nodes.get(0));
    Assert.assertEquals("2001:db8:0:0:0:0:0:2", nodes.get(1).getHostV6());
    Assert.assertEquals(18889, nodes.get(1).getPort());
  }

  @Test
  public void testCompressDnsNode() throws UnknownHostException, InvalidProtocolBufferException {
    DnsNode[] nodes = new DnsNode[] {
        new DnsNode(null, "192.168.0.1", null, 10000),
    };
    List<DnsNode> nodeList = Arrays.asList(nodes);
    String enrContent = DnsNode.compress(nodeList);

    List<DnsNode> dnsNodes = DnsNode.decompress(enrContent);
    Assert.assertEquals(1, dnsNodes.size());
    Assert.assertTrue(nodes[0].equals(dnsNodes.get(0)));
  }

  @Test
  public void testSortDnsNode() throws UnknownHostException {
    DnsNode[] nodes = new DnsNode[] {
        new DnsNode(null, "192.168.0.1", null, 10000),
        new DnsNode(null, "192.168.0.2", null, 10000),
        new DnsNode(null, "192.168.0.3", null, 10000),
        new DnsNode(null, "192.168.0.4", null, 10000),
        new DnsNode(null, "192.168.0.5", null, 10000),
        new DnsNode(null, "192.168.0.6", null, 10001),
        new DnsNode(null, "192.168.0.6", null, 10002),
        new DnsNode(null, "192.168.0.6", null, 10003),
        new DnsNode(null, "192.168.0.6", null, 10004),
        new DnsNode(null, "192.168.0.6", null, 10005),
        new DnsNode(null, "192.168.0.10", "fe80::0001", 10005),
        new DnsNode(null, "192.168.0.10", "fe80::0002", 10005),
        new DnsNode(null, null, "fe80::0001", 10000),
        new DnsNode(null, null, "fe80::0002", 10000),
        new DnsNode(null, null, "fe80::0002", 10001),
    };
    List<DnsNode> nodeList = Arrays.asList(nodes);
    Collections.shuffle(nodeList); //random order
    Collections.sort(nodeList);
    for (int i = 0; i < nodeList.size(); i++) {
      Assert.assertTrue(nodes[i].equals(nodeList.get(i)));
    }
  }
}
