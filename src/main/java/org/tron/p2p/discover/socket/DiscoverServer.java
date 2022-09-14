package org.tron.p2p.discover.socket;

import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j(topic = "discover")
@Component
public class DiscoverServer {

  @Scheduled(cron = "${task.cron}")
  public void testCron() {
    log.info("test");
  }
}
