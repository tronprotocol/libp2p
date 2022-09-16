package org.tron.p2p;

import java.net.InetSocketAddress;
import java.util.List;

public class P2pConfig {

  private List<InetSocketAddress> seedNodes;
  private List<InetSocketAddress> activeNodes;
  private List<InetSocketAddress> trustNodes;
  private final byte[] nodeID;
  private final String ip;
  private final int port; //udp port and tcp port
  private final int version;
  private int minConnections = 10;
  private int maxConnections = 50;
  private int minActiveConnections = 6;
  private int maxConnectionsWithSameIp = 2;
  private boolean discoverEnable = false;
  private boolean disconnectionPolicyEnable = false;

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
}
