package org.tron.p2p;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.tron.p2p.config.Parameter;
import org.tron.p2p.connection.ChannelManager;
import org.tron.p2p.discover.NodeManager;
import org.tron.p2p.stats.P2pStats;
import org.tron.p2p.stats.StatsManager;

import java.net.InetSocketAddress;

@Slf4j
public class P2pService {

  private NodeManager nodeManager = new NodeManager();

  private ChannelManager channelManager = new ChannelManager();

  private StatsManager statsManager = new StatsManager();

  public void start(P2pConfig p2pConfig) {
    Parameter.p2pConfig = p2pConfig;
    nodeManager.init();
    channelManager.init();
    log.info("P2p service started.");
  }

  public void close() {
    nodeManager.close();
    channelManager.close();
    log.info("P2p service closed.");
  }

  public void register(P2pEventHandle p2pEventHandle) {
    Parameter.addP2pEventHandle(p2pEventHandle);
  }

  public void connect(InetSocketAddress address) {
    channelManager.connect(address);
  }

  public P2pStats getP2pStats() {
    return statsManager.getP2pStats();
  }

}
