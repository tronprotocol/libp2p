package org.tron.p2p;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.List;
import lombok.Data;
import org.tron.p2p.utils.NetUtil;

@Data
public class P2pConfig {
  private List<InetSocketAddress> seedNodes = new ArrayList<>();
  private List<InetSocketAddress> activeNodes = new ArrayList<>();
  private List<InetAddress> trustNodes = new ArrayList<>();
  private byte[] nodeID = NetUtil.getNodeId();
  private String ip = NetUtil.getExternalIpV4();
  private String ipv6 = NetUtil.getExternalIpV6();
  private int port = 18888;
  private int version = 1;
  private int minConnections = 8;
  private int maxConnections = 50;
  private int minActiveConnections = 2;
  private int maxConnectionsWithSameIp = 2;
  private boolean discoverEnable = true;
  private boolean disconnectionPolicyEnable = false;

  private List<String> entreeUrls = new ArrayList<>();
}
