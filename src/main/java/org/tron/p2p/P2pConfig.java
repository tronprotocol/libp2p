package org.tron.p2p;

import java.net.InetSocketAddress;
import java.util.List;

public class P2pConfig {
  private final List<InetSocketAddress> seedNodes;
  private final List<InetSocketAddress> activeNodes;
  private final List<InetSocketAddress> trustNodes;
  private final byte[] nodeID;
  private final String ip;
  private final int port;
  private final int version;
  private final int maxConnections;
  private final int minConnections;
  private final int minActiveConnections;
  private final int maxConnectionsWithSameIp;
  private final boolean discoverEnable;
  private final boolean disconnectionPolicyEnable;

  public P2pConfig(List<InetSocketAddress> seedNodes,
                   List<InetSocketAddress> activeNodes,
                   List<InetSocketAddress> trustNodes,
                   byte[] nodeID,
                   String ip,
                   int port,
                   int version,
                   int maxConnections,
                   int minConnections,
                   int minActiveConnections,
                   int maxConnectionsWithSameIp,
                   boolean discoverEnable,
                   boolean disconnectionPolicyEnable) {
    this.seedNodes = seedNodes;
    this.activeNodes = activeNodes;
    this.trustNodes = trustNodes;
    this.nodeID = nodeID;
    this.ip = ip;
    this.port = port;
    this.version = version;
    this.maxConnections = maxConnections;
    this.minConnections = minConnections;
    this.minActiveConnections = minActiveConnections;
    this.maxConnectionsWithSameIp = maxConnectionsWithSameIp;
    this.discoverEnable = discoverEnable;
    this.disconnectionPolicyEnable = disconnectionPolicyEnable;
  }
}
