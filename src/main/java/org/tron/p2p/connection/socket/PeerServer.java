package org.tron.p2p.connection.socket;


import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelOption;
import io.netty.channel.DefaultMessageSizeEstimator;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.logging.LoggingHandler;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.tron.p2p.base.Parameter;
import org.tron.p2p.connection.ChannelManager;
import org.tron.p2p.discover.NodeManager;

@Slf4j(topic = "net")
public class PeerServer {

  private boolean listening;
  private ChannelManager channelManager;
  @Setter
  @Getter
  private NodeManager nodeManager;
  private ChannelFuture channelFuture;

  public PeerServer(ChannelManager channelManager) {
    this.channelManager = channelManager;
  }

  public void start(int port) {

    EventLoopGroup bossGroup = new NioEventLoopGroup(1);
    EventLoopGroup workerGroup = new NioEventLoopGroup(Parameter.tcpNettyWorkThreadNum);
    P2pChannelInitializer tronChannelInitializer = new P2pChannelInitializer("", channelManager,
        nodeManager);

    try {
      ServerBootstrap b = new ServerBootstrap();

      b.group(bossGroup, workerGroup);
      b.channel(NioServerSocketChannel.class);

      b.option(ChannelOption.MESSAGE_SIZE_ESTIMATOR, DefaultMessageSizeEstimator.DEFAULT);
      b.option(ChannelOption.CONNECT_TIMEOUT_MILLIS, Parameter.nodeConnectionTimeout);

      b.handler(new LoggingHandler());
      b.childHandler(tronChannelInitializer);

      // Start the client.
      log.info("TCP listener started, bind port {}", port);

      channelFuture = b.bind(port).sync();

      listening = true;

      // Wait until the connection is closed.
      channelFuture.channel().closeFuture().sync();

      log.info("TCP listener closed");

    } catch (Exception e) {
      log.error("Start TCP server failed.", e);
    } finally {
      workerGroup.shutdownGracefully();
      bossGroup.shutdownGracefully();
      listening = false;
    }
  }

  public void close() {
    if (listening && channelFuture != null && channelFuture.channel().isOpen()) {
      try {
        log.info("Closing TCP server...");
        channelFuture.channel().close().sync();
      } catch (Exception e) {
        log.warn("Closing TCP server failed.", e);
      }
    }
  }

}
