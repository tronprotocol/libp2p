package org.tron.p2p.connection.business;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.tron.p2p.P2pService;

@Slf4j(topic = "net")
public class KeepAliveTask {

  @Autowired
  private P2pService p2pService;

  private ScheduledExecutorService executor =
      Executors.newSingleThreadScheduledExecutor(r -> new Thread(r, "KeepAliveTask"));

  public void init() {
    executor.scheduleWithFixedDelay(() -> {
      try {
        long now = System.currentTimeMillis();
        p2pService.getChannels().forEach(p -> {
          if (now - p.getLastSendTime() > 10_000) {
            // 1. send ping to p
          }
        });
      } catch (Throwable t) {
        log.error("Exception in keep alive task.", t);
      }
    }, 2, 2, TimeUnit.SECONDS);
  }
}
