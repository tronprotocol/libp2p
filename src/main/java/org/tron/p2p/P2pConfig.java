package org.tron.p2p;

import java.net.InetSocketAddress;
import java.util.List;
import lombok.Getter;

public class P2pConfig {

  @Getter
  private List<InetSocketAddress> seedNodes;
  @Getter
  private List<InetSocketAddress> activeNodes;
  @Getter
  private List<InetSocketAddress> trustNodes;
  @Getter
  private final byte[] nodeID;
  @Getter
  private final String ip;
  @Getter
  private final int port; //udp port and tcp port
  @Getter
  private final int version;
  @Getter
  private int minConnections = 10;
  @Getter
  private int maxConnections = 50;
  @Getter
  private int minActiveConnections = 6;
  @Getter
  private int maxConnectionsWithSameIp = 2;
  @Getter
  private boolean discoverEnable = false;
  @Getter
  private boolean disconnectionPolicyEnable = false;
  @Getter
  private int discoveryPingTimeOut = 15000;

  public P2pConfig(byte[] nodeID, String ip, int port, int version) {
    this.nodeID = nodeID;
    this.ip = ip;
    this.port = port;
    this.version = version;
  }

  public P2pConfig withSeedNodes(List<InetSocketAddress> seedNodes) {
    this.seedNodes = seedNodes;
    return this;
  }

  public P2pConfig withTrustNodes(List<InetSocketAddress> trustNodes) {
    this.trustNodes = trustNodes;
    return this;
  }

  public P2pConfig withActiveNodes(List<InetSocketAddress> activeNodes) {
    this.activeNodes = activeNodes;
    return this;
  }

  public P2pConfig withMinConnections(int minConnections) {
    this.minConnections = minConnections;
    return this;
  }

  public P2pConfig withMaxConnections(int maxConnections) {
    this.maxConnections = maxConnections;
    return this;
  }

  public P2pConfig withMinActiveConnections(int minActiveConnections) {
    this.minActiveConnections = minActiveConnections;
    return this;
  }

  public P2pConfig withMaxConnectionsWithSameIp(int maxConnectionsWithSameIp) {
    this.maxConnectionsWithSameIp = maxConnectionsWithSameIp;
    return this;
  }

  public P2pConfig withDiscoverEnable(boolean discoverEnable) {
    this.discoverEnable = discoverEnable;
    return this;
  }

  public P2pConfig withDisconnectionPolicyEnable(boolean disconnectionPolicyEnable) {
    this.disconnectionPolicyEnable = disconnectionPolicyEnable;
    return this;
  }

  public P2pConfig withDiscoveryPingTimeOut(int discoveryPingTimeOut) {
    this.discoveryPingTimeOut = discoveryPingTimeOut;
    return this;
  }
}
