package org.tron.p2p.connection.business.keepalive;

import static org.tron.p2p.base.Parameter.keepAlivePeriod;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.tron.p2p.connection.Channel;
import org.tron.p2p.connection.ChannelManager;
import org.tron.p2p.connection.message.Message;
import org.tron.p2p.connection.message.keepalive.PingMessage;
import org.tron.p2p.connection.message.keepalive.PongMessage;

@Slf4j(topic = "net")
public class KeepAliveService {

  private ScheduledExecutorService executor =
      Executors.newSingleThreadScheduledExecutor(r -> new Thread(r, "KeepAlive"));

  public void init() {
    executor.scheduleWithFixedDelay(() -> {
      try {
        long now = System.currentTimeMillis();
        ChannelManager.getChannels().values().forEach(p -> {
          if (!p.waitForPong && now - p.getLastSendTime() > keepAlivePeriod) {
            p.send(new PingMessage().getData());
            p.waitForPong = true;
          }
        });
      } catch (Throwable t) {
        log.error("Exception in keep alive task.", t);
      }
    }, 2, 2, TimeUnit.SECONDS);
  }

  public void close() {
    executor.shutdown();
  }

  public void processPingMessage(Channel channel, Message message) {
    channel.send(new PongMessage().getData());
  }

  public void processPongMessage(Channel channel, Message message) {
    channel.waitForPong = false;
  }
}
