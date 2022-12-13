package org.tron.p2p.dns;

import org.tron.p2p.discover.Node;

import java.util.List;

public class DnsManager {

  private static SyncService syncService;
  private static UpdateService updateService;


  public static void init() {
    syncService = new SyncService();
    updateService = new UpdateService();
    syncService.init();
    updateService.init();
  }

  public static void close() {
    syncService.close();
    updateService.close();
  }

  public static List<Node> getNodes() {
    return syncService.getNodes();
  }
}
