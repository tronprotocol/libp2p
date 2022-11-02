package org.tron.p2p.discover.protocol.kad;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.tron.p2p.base.Parameter;
import org.tron.p2p.discover.DiscoverService;
import org.tron.p2p.discover.Node;
import org.tron.p2p.discover.message.kad.FindNodeMessage;
import org.tron.p2p.discover.message.kad.KadMessage;
import org.tron.p2p.discover.message.kad.NeighborsMessage;
import org.tron.p2p.discover.message.kad.PingMessage;
import org.tron.p2p.discover.message.kad.PongMessage;
import org.tron.p2p.discover.protocol.kad.table.NodeTable;
import org.tron.p2p.discover.socket.UdpEvent;

@Slf4j(topic = "net")
public class KadService implements DiscoverService {

  private static final int MAX_NODES = 2000;
  private static final int NODES_TRIM_THRESHOLD = 3000;
  @Getter
  @Setter
  private static long pingTimeout = 15_000;

  private final List<Node> bootNodes = new ArrayList<>();

  private volatile boolean inited = false;

  private final Map<String, NodeHandler> nodeHandlerMap = new ConcurrentHashMap<>();

  private Consumer<UdpEvent> messageSender;

  private NodeTable table;
  private Node homeNode;

  private ScheduledExecutorService pongTimer;
  private DiscoverTask discoverTask;

  private static Map<InetSocketAddress, Integer> host2Key = new ConcurrentHashMap<>();
  private static Map<Integer, Set<InetSocketAddress>> key2Host = new ConcurrentHashMap<>();

  public void init() {
    for (InetSocketAddress address : Parameter.p2pConfig.getSeedNodes()) {
      bootNodes.add(new Node(address));
    }
    for (InetSocketAddress address : Parameter.p2pConfig.getActiveNodes()) {
      bootNodes.add(new Node(address));
    }
    this.pongTimer = Executors.newSingleThreadScheduledExecutor();
    this.homeNode = new Node(Parameter.p2pConfig.getNodeID(), Parameter.p2pConfig.getIp(),
        Parameter.p2pConfig.getIpv6(), Parameter.p2pConfig.getPort());
    this.table = new NodeTable(homeNode);

    if (Parameter.p2pConfig.isDiscoverEnable()) {
      discoverTask = new DiscoverTask(this);
      discoverTask.init();
    }
  }

  public void close() {
    try {
      if (pongTimer != null) {
        pongTimer.shutdownNow();
      }

      if (discoverTask != null) {
        discoverTask.close();
      }
    } catch (Exception e) {
      log.error("Close nodeManagerTasksTimer or pongTimer failed", e);
      throw e;
    }
  }

  public List<Node> getConnectableNodes() {
    return getAllNodes().stream()
        .filter(node -> node.isConnectible(Parameter.p2pConfig.getVersion()))
        .filter(Node::isIpStackCompatible)
        .collect(Collectors.toList());
  }

  public List<Node> getTableNodes() {
    return table.getTableNodes();
  }

  public List<Node> getAllNodes() {
    List<Node> nodeList = new ArrayList<>();
    for (NodeHandler nodeHandler : nodeHandlerMap.values()) {
      nodeList.add(nodeHandler.getNode());
    }
    return nodeList;
  }

  @Override
  public void setMessageSender(Consumer<UdpEvent> messageSender) {
    this.messageSender = messageSender;
  }

  @Override
  public void channelActivated() {
    if (!inited) {
      inited = true;

      for (Node node : bootNodes) {
        getNodeHandler(node);
      }
    }
  }

  @Override
  public void handleEvent(UdpEvent udpEvent) {
    KadMessage m = (KadMessage) udpEvent.getMessage();

    InetSocketAddress sender = udpEvent.getAddress();
    Node n = new Node(m.getFrom().getId(), m.getFrom().getHostV4(), m.getFrom().getHostV6(),
        sender.getPort(), m.getFrom().getPort());
    boolean useV4 = sender.getAddress() instanceof Inet4Address;

    NodeHandler nodeHandler = getNodeHandler(n);
    nodeHandler.getNode().setId(n.getId());
    nodeHandler.getNode().touch();

    switch (m.getType()) {
      case KAD_PING:
        nodeHandler.handlePing((PingMessage) m, useV4);
        break;
      case KAD_PONG:
        nodeHandler.handlePong((PongMessage) m);
        break;
      case KAD_FIND_NODE:
        nodeHandler.handleFindNode((FindNodeMessage) m, useV4);
        break;
      case KAD_NEIGHBORS:
        nodeHandler.handleNeighbours((NeighborsMessage) m);
        break;
      default:
        break;
    }
  }

  public NodeHandler getNodeHandler(Node n) {
    String key = getKey(n);
    NodeHandler ret = nodeHandlerMap.get(key);
    if (ret == null) {
      trimTable();
      ret = new NodeHandler(n, this);
    } else {
      ret.getNode().updateHostV4(n.getHostV4());
      ret.getNode().updateHostV6(n.getHostV6());
    }
    nodeHandlerMap.put(key, ret);
    return ret;
  }

  public NodeTable getTable() {
    return table;
  }

  public Node getPublicHomeNode() {
    return homeNode;
  }

  public void sendOutbound(UdpEvent udpEvent) {
    if (Parameter.p2pConfig.isDiscoverEnable() && messageSender != null) {
      messageSender.accept(udpEvent);
    }
  }

  public ScheduledExecutorService getPongTimer() {
    return pongTimer;
  }

  private void trimTable() {
    if (nodeHandlerMap.size() > NODES_TRIM_THRESHOLD) {
      nodeHandlerMap.values().forEach(handler -> {
        if (!handler.getNode().isConnectible(Parameter.p2pConfig.getVersion())) {
          nodeHandlerMap.values().remove(handler);
        }
      });
    }
    if (nodeHandlerMap.size() > NODES_TRIM_THRESHOLD) {
      List<NodeHandler> sorted = new ArrayList<>(nodeHandlerMap.values());
      sorted.sort(Comparator.comparingLong(o -> o.getNode().getUpdateTime()));
      for (NodeHandler handler : sorted) {
        nodeHandlerMap.values().remove(handler);
        if (nodeHandlerMap.size() <= MAX_NODES) {
          break;
        }
      }
    }
  }

  private String getKey(Node n) {
    return getKey(n.getHostV4(), n.getHostV6(), n.getPort());
  }

  // if hostV4:port or hostV6:port exist, we consider they are the same node. orders may like this:
  // first node with v4, then node with v4 & v6
  // first node with v6, then node with v4 & v6
  // first node with v4 & v6, then node with v4
  // first node with v4 & v6, then node with v6
  private String getKey(String hostV4, String hostV6, int port) {

    if ((StringUtils.isNotEmpty(hostV4) && StringUtils.isEmpty(hostV6)) || (
        StringUtils.isEmpty(hostV4) && StringUtils.isNotEmpty(hostV6))) {

      InetSocketAddress inet = StringUtils.isNotEmpty(hostV4) ? new InetSocketAddress(hostV4, port)
          : new InetSocketAddress(hostV6, port);

      if (host2Key.containsKey(inet)) {
        return String.valueOf(host2Key.get(inet));
      } else {
        int group = host2Key.size();
        host2Key.put(inet, group);
        Set<InetSocketAddress> inetSet = key2Host.getOrDefault(group, new HashSet<>());
        inetSet.add(inet);
        key2Host.put(group, inetSet);
        return String.valueOf(group);
      }
    } else if (StringUtils.isNotEmpty(hostV4) && StringUtils.isNotEmpty(hostV6)) {

      InetSocketAddress inet4 = new InetSocketAddress(hostV4, port);
      InetSocketAddress inet6 = new InetSocketAddress(hostV6, port);

      int group;
      if (host2Key.containsKey(inet4)) {
        group = host2Key.get(inet4);
        if (!host2Key.containsKey(inet6)) {
          host2Key.put(inet6, group);
        }
      } else if (host2Key.containsKey(inet6)) {
        group = host2Key.get(inet6);
        if (!host2Key.containsKey(inet4)) {
          host2Key.put(inet4, group);
        }
      } else {
        group = host2Key.size();
        host2Key.put(inet4, group);
        host2Key.put(inet6, group);
      }

      Set<InetSocketAddress> inetSet = key2Host.getOrDefault(group, new HashSet<>());
      inetSet.add(inet4);
      inetSet.add(inet6);
      key2Host.put(group, inetSet);

      return String.valueOf(group);
    } else {
      //impossible
      return null;
    }
  }

  public static InetSocketAddress matchDifferentIpStack(InetSocketAddress inetSocketAddress) {
    InetSocketAddress match = null;
    if (!host2Key.containsKey(inetSocketAddress)) {
      return null;
    }
    int group = host2Key.get(inetSocketAddress);
    if (!key2Host.containsKey(group)) {
      return null;
    }
    Set<InetSocketAddress> inetSet = key2Host.get(group);
    for (InetSocketAddress inet : inetSet) {
      if (!inet.equals(inetSocketAddress)) {
        match = inet;
        break;
      }
    }
    return match;
  }
}
