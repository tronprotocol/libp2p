package org.tron.p2p.discover.protocol.kad;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import lombok.extern.slf4j.Slf4j;
import org.tron.p2p.base.Parameter;
import org.tron.p2p.discover.DiscoverService;
import org.tron.p2p.discover.Node;
import org.tron.p2p.discover.message.Message;
import org.tron.p2p.discover.message.kad.KadMessage;
import org.tron.p2p.discover.protocol.kad.table.NodeTable;
import org.tron.p2p.discover.socket.UdpEvent;
import org.tron.p2p.discover.message.kad.FindNodeMessage;
import org.tron.p2p.discover.message.kad.NeighborsMessage;
import org.tron.p2p.discover.message.kad.PingMessage;
import org.tron.p2p.discover.message.kad.PongMessage;

@Slf4j(topic = "net")
public class KadService implements DiscoverService {

  private static final int MAX_NODES = 2000;
  private static final int NODES_TRIM_THRESHOLD = 3000;

  private List<Node> bootNodes = new ArrayList<>();

  private volatile boolean inited = false;

  private Map<String, NodeHandler> nodeHandlerMap = new ConcurrentHashMap<>();

  private Consumer<UdpEvent> messageSender;

  private NodeTable table;
  private Node homeNode;

  private ScheduledExecutorService pongTimer;
  private DiscoverTask discoverTask;

  public void init() {
    log.debug("KadService init");
    for (InetSocketAddress boot : Parameter.p2pConfig.getSeedNodes()) {
      bootNodes.add(Node.instanceOf(boot.getHostString(), boot.getPort()));
    }
    this.pongTimer = Executors.newSingleThreadScheduledExecutor();
    this.homeNode = new Node(Node.getNodeId(), Parameter.p2pConfig.getIp(),
        Parameter.p2pConfig.getPort());
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

  public Node initNode(Node node) {
   return getNodeHandler(node).getNode();
  }

  public List<Node> getConnectableNodes() {
    return getAllNodes().stream()
        .filter(node -> node.isConnectible(Parameter.p2pConfig.getVersion()))
        .sorted(Comparator.comparingLong(node -> -node.getUpdateTime()))
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
    log.debug("channel activated");
    if (!inited) {
      log.debug("start process boot nodes, size:{}", bootNodes.size());
      inited = true;

      for (Node node : bootNodes) {
        getNodeHandler(node);
      }
    }
  }

  @Override
  public void handleEvent(UdpEvent udpEvent) {
    KadMessage m = (KadMessage)udpEvent.getMessage();

    InetSocketAddress sender = udpEvent.getAddress();

    Node n = new Node(m.getFrom().getId(), sender.getHostString(), sender.getPort(),
        m.getFrom().getPort());

    NodeHandler nodeHandler = getNodeHandler(n);
    nodeHandler.getNode().setId(n.getId());
    nodeHandler.getNode().touch();
    //nodeHandler.getNodeStatistics().messageStatistics.addUdpInMessage(m.getType());
    //int length = udpEvent.getMessage().getData().length + 1;
    //MetricsUtil.meterMark(MetricsKey.NET_UDP_IN_TRAFFIC, length);
    //Metrics.histogramObserve(MetricKeys.Histogram.UDP_BYTES, length,
    //    MetricLabels.Histogram.TRAFFIC_IN);

    switch (m.getType()) {
      case KAD_PING:
        nodeHandler.handlePing((PingMessage) m);
        break;
      case KAD_PONG:
        nodeHandler.handlePong((PongMessage) m);
        break;
      case KAD_FIND_NODE:
        nodeHandler.handleFindNode((FindNodeMessage) m);
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
      nodeHandlerMap.put(key, ret);
    }
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
//      int length = udpEvent.getMessage().getSendData().length;
//      MetricsUtil.meterMark(MetricsKey.NET_UDP_OUT_TRAFFIC, length);
//      Metrics.histogramObserve(MetricKeys.Histogram.UDP_BYTES, length,
//          MetricLabels.Histogram.TRAFFIC_OUT);

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
    return getKey(new InetSocketAddress(n.getHost(), n.getPort()));
  }

  private String getKey(InetSocketAddress address) {
    InetAddress inetAddress = address.getAddress();
    return (inetAddress == null ? address.getHostString() : inetAddress.getHostAddress()) + ":"
        + address.getPort();
  }
}
