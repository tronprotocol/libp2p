package org.tron.p2p.connection.business.keepalive;

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
          if (now - p.getLastSendTime() > 20_000) {
            p.send(new PingMessage().getData());
          }
          if (now - p.getLastSendTime() > 60_000) {
            //disconnect if we has not receive ping or pong from channel too long
            p.close();
          }
        });
      } catch (Throwable t) {
        log.error("Exception in keep alive task.", t);
      }
    }, 20, 20, TimeUnit.SECONDS);
  }

  public void close() {
    executor.shutdown();
  }

  public void processPingMessage(Channel channel, Message message) {
    PingMessage pingMessage = (PingMessage) message;
    long latency = System.currentTimeMillis() - pingMessage.getTimeStamp();
    //todo statistic latency
    channel.send(new PongMessage().getData());
    channel.setLastSendTime(System.currentTimeMillis());
  }

  public void processPongMessage(Channel channel, Message message) {
    PongMessage pongMessage = (PongMessage) message;
    long latency = System.currentTimeMillis() - pongMessage.getTimeStamp();
    //todo statistic latency
    channel.setLastSendTime(System.currentTimeMillis());
  }
}
