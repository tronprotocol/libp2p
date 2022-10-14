package org.tron.p2p.connection.business.keepalive;

import static org.tron.p2p.base.Parameter.KEEP_ALIVE_PERIOD;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.tron.p2p.connection.Channel;
import org.tron.p2p.connection.ChannelManager;
import org.tron.p2p.connection.business.MessageProcess;
import org.tron.p2p.connection.message.Message;
import org.tron.p2p.connection.message.keepalive.PingMessage;
import org.tron.p2p.connection.message.keepalive.PongMessage;

@Slf4j(topic = "net")
public class KeepAliveService implements MessageProcess {

  private final ScheduledExecutorService executor =
      Executors.newSingleThreadScheduledExecutor(r -> new Thread(r, "KeepAlive"));

  public void init() {
    executor.scheduleWithFixedDelay(() -> {
      try {
        long now = System.currentTimeMillis();
        ChannelManager.getChannels().values().stream()
            .filter(p -> !p.isDisconnect())
            .forEach(p -> {
              if ((!p.waitForPong && now - p.getLastSendTime() > KEEP_ALIVE_PERIOD)
                  || (now - p.pingSent > KEEP_ALIVE_PERIOD)) {
                p.send(new PingMessage());
                p.waitForPong = true;
                p.pingSent = now;
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

  @Override
  public void processMessage(Channel channel, Message message) {
    switch (message.getType()) {
      case KEEP_ALIVE_PING:
        channel.send(new PongMessage());
        break;
      case KEEP_ALIVE_PONG:
        channel.waitForPong = false;
        break;
      default:
        break;
    }
  }
}
