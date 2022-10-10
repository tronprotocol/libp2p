package org.tron.p2p.connection;

import io.netty.channel.ChannelFutureListener;
import java.net.InetSocketAddress;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.tron.p2p.P2pConfig;
import org.tron.p2p.base.Parameter;
import org.tron.p2p.connection.message.Message;
import org.tron.p2p.connection.message.keepalive.PingMessage;
import org.tron.p2p.discover.Node;
import org.tron.p2p.discover.NodeManager;

public class SocketTest {

  private static String localIp = "127.0.0.1";
  private static int port = 10000;

  @Before
  public void init() {
    Parameter.p2pConfig = new P2pConfig();
    Parameter.p2pConfig.setIp(localIp);
    Parameter.p2pConfig.setPort(port);
    Parameter.p2pConfig.setDiscoverEnable(false);

    NodeManager.init();
    ChannelManager.init();
  }

  private boolean sendMessage(io.netty.channel.Channel nettyChannel, Message message) {
    AtomicBoolean sendSuccess = new AtomicBoolean(false);
    nettyChannel.writeAndFlush(message.getSendData())
        .addListener((ChannelFutureListener) future -> {
          if (future.isSuccess()) {
            sendSuccess.set(true);
          } else {
            sendSuccess.set(false);
          }
        });
    try {
      Thread.sleep(4000);
    } catch (InterruptedException e) {
      throw new RuntimeException(e);
    }
    return sendSuccess.get();
  }

  @Test
  public void testPeerServerAndPeerClient() {
    //peer server is already start at this port
    Node node = new Node(new InetSocketAddress(localIp, port));

    //peer client try to connect peer server using random port
    io.netty.channel.Channel nettyChannel = ChannelManager.getPeerClient().connectAsync(node, false)
        .channel();

    PingMessage pingMessage = new PingMessage();
    boolean sendSuccess = sendMessage(nettyChannel, pingMessage);
    Assert.assertTrue(sendSuccess);
  }

  @After
  public void destroy() {
    NodeManager.close();
    ChannelManager.close();
  }
}
