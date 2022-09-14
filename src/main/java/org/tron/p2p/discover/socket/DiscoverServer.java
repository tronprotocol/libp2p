package org.tron.p2p.discover.socket;

import org.springframework.scheduling.annotation.Scheduled;

public class DiscoverServer {

  @Scheduled(cron = "${task.cron}")
  public void testCron() {
  }
}
