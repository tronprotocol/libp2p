package org.tron.p2p.discover.socket;

import java.util.concurrent.TimeUnit;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioDatagramChannel;
import io.netty.handler.codec.protobuf.ProtobufVarint32FrameDecoder;
import io.netty.handler.codec.protobuf.ProtobufVarint32LengthFieldPrepender;
import lombok.extern.slf4j.Slf4j;
import org.tron.p2p.base.Parameter;

@Slf4j(topic = "net")
public class DiscoverServer {

  private static final int SERVER_RESTART_WAIT = 5000;
  private static final int SERVER_CLOSE_WAIT = 10;

  private Channel channel;

  private int port = Parameter.p2pConfig.getPort();

  private volatile boolean shutdown = false;

  public void init(EventHandler eventHandler) throws Exception {
    // todo threads num config
    NioEventLoopGroup group = new NioEventLoopGroup(1);
    try {
      while (!shutdown) {
        Bootstrap b = new Bootstrap();
        b.group(group)
            .channel(NioDatagramChannel.class)
            .handler(new ChannelInitializer<NioDatagramChannel>() {
              @Override
              public void initChannel(NioDatagramChannel ch)
                  throws Exception {
                //ch.pipeline().addLast(stats.udp);
                ch.pipeline().addLast(new ProtobufVarint32LengthFieldPrepender());
                ch.pipeline().addLast(new ProtobufVarint32FrameDecoder());
                ch.pipeline().addLast(new PacketDecoder());
                MessageHandler messageHandler = new MessageHandler(ch, eventHandler);
                eventHandler.setMessageSender(messageHandler);
                ch.pipeline().addLast(messageHandler);
              }
            });

        channel = b.bind(port).sync().channel();

        log.info("Discovery server started, bind port {}", port);

        channel.closeFuture().sync();
        if (shutdown) {
          log.info("Shutdown discovery server");
          break;
        }
        log.warn("Restart discovery server after 5 sec pause...");
        Thread.sleep(SERVER_RESTART_WAIT);
      }
    } catch (InterruptedException e) {
      log.warn("Discover server interrupted");
      Thread.currentThread().interrupt();
    } catch (Exception e) {
      log.error("Start discovery server with port {} failed", port, e);
    } finally {
      group.shutdownGracefully().sync();
    }
  }

  public void close() {
    log.info("Closing discovery server...");
    shutdown = true;
    if (channel != null) {
      try {
        channel.close().await(SERVER_CLOSE_WAIT, TimeUnit.SECONDS);
      } catch (Exception e) {
        log.error("Closing discovery server failed", e);
      }
    }
  }
}
