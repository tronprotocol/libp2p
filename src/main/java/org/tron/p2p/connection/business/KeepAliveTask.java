package org.tron.p2p.connection.business;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.tron.p2p.connection.ChannelManager;
import org.tron.p2p.connection.message.TcpPingMessage;

@Slf4j(topic = "net")
public class KeepAliveTask {

  private ChannelManager channelManager;

  private ScheduledExecutorService executor =
      Executors.newSingleThreadScheduledExecutor(r -> new Thread(r, "KeepAliveTask"));

  public void init(ChannelManager channelManager) {
    this.channelManager = channelManager;
    executor.scheduleWithFixedDelay(() -> {
      try {
        long now = System.currentTimeMillis();
        ChannelManager.getChannels().values().forEach(p -> {
          if (now - p.getLastSendTime() > 10_000) {
            // 1. send ping to p
            p.send(new TcpPingMessage().getData());
          }
        });
      } catch (Throwable t) {
        log.error("Exception in keep alive task.", t);
      }
    }, 2, 2, TimeUnit.SECONDS);
  }
}
