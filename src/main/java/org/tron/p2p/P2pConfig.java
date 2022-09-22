package org.tron.p2p;

import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.List;

import lombok.Data;
import lombok.Getter;
import org.tron.p2p.discover.Node;
import org.tron.p2p.utils.NetUtil;

@Data
public class P2pConfig {
  private List<InetSocketAddress> seedNodes = new ArrayList<>();
  private List<InetSocketAddress> activeNodes = new ArrayList<>();
  private List<InetSocketAddress> trustNodes = new ArrayList<>();
  private byte[] nodeID = Node.getNodeId();
  private String ip = NetUtil.getExternalIp();
  private int port = 18888;
  private int version = 1;
  private int minConnections = 8;
  private int maxConnections = 50;
  private int minActiveConnections = 2;
  private int maxConnectionsWithSameIp = 2;
  private boolean discoverEnable = true;
  private boolean disconnectionPolicyEnable = false;
  @Getter
  private int discoveryPingTimeOut = 15000;

  public P2pConfig(String ip, int port, int version) {
    this.nodeID = Node.getNodeId();
    this.ip = ip;
    this.port = port;
    this.version = version;
  }

  public P2pConfig(byte[] nodeID, String ip, int port, int version) {
    this.nodeID = nodeID;
    this.ip = ip;
    this.port = port;
    this.version = version;
  }

  public P2pConfig setSeedNodes(List<InetSocketAddress> seedNodes) {
    this.seedNodes = seedNodes;
    return this;
  }

  public P2pConfig setTrustNodes(List<InetSocketAddress> trustNodes) {
    this.trustNodes = trustNodes;
    return this;
  }

  public P2pConfig setActiveNodes(List<InetSocketAddress> activeNodes) {
    this.activeNodes = activeNodes;
    return this;
  }

  public P2pConfig setMinConnections(int minConnections) {
    this.minConnections = minConnections;
    return this;
  }

  public P2pConfig setMaxConnections(int maxConnections) {
    this.maxConnections = maxConnections;
    return this;
  }

  public P2pConfig setMinActiveConnections(int minActiveConnections) {
    this.minActiveConnections = minActiveConnections;
    return this;
  }

  public P2pConfig setMaxConnectionsWithSameIp(int maxConnectionsWithSameIp) {
    this.maxConnectionsWithSameIp = maxConnectionsWithSameIp;
    return this;
  }

  public P2pConfig setDiscoverEnable(boolean discoverEnable) {
    this.discoverEnable = discoverEnable;
    return this;
  }

  public P2pConfig setDisconnectionPolicyEnable(boolean disconnectionPolicyEnable) {
    this.disconnectionPolicyEnable = disconnectionPolicyEnable;
    return this;
  }

  public P2pConfig setDiscoveryPingTimeOut(int discoveryPingTimeOut) {
    this.discoveryPingTimeOut = discoveryPingTimeOut;
    return this;
  }
}
