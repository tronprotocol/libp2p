package org.tron.p2p;

import java.net.InetSocketAddress;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.tron.p2p.base.Parameter;
import org.tron.p2p.connection.Channel;
import org.tron.p2p.connection.ChannelManager;
import org.tron.p2p.discover.Node;
import org.tron.p2p.discover.NodeManager;
import org.tron.p2p.stats.P2pStats;
import org.tron.p2p.stats.StatsManager;

@Slf4j(topic = "net")
public class P2pService {

  private StatsManager statsManager = new StatsManager();

  public void start(P2pConfig p2pConfig) {
    Parameter.p2pConfig = p2pConfig;
    NodeManager.init();
    ChannelManager.init();
    log.info("P2p service started");
  }

  public void close() {
    NodeManager.close();
    ChannelManager.close();
    log.info("P2p service closed");
  }

  public void register(P2pEventHandler p2PEventHandler) {
    Parameter.addP2pEventHandle(p2PEventHandler);
  }

  public void connect(InetSocketAddress address) {
    ChannelManager.connect(address);
  }

  public P2pStats getP2pStats() {
    return statsManager.getP2pStats();
  }

  public List<Node> getTableNodes() {
    return NodeManager.getTableNodes();
  }

  public List<Node> getAllNodes() {
    return NodeManager.getAllNodes();
  }

}
