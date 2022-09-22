package org.tron.p2p.connection;

import com.google.common.base.Throwables;
import com.google.protobuf.ByteString;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPipeline;
import io.netty.handler.codec.protobuf.ProtobufVarint32LengthFieldPrepender;
import io.netty.handler.timeout.ReadTimeoutException;
import io.netty.handler.timeout.ReadTimeoutHandler;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.concurrent.TimeUnit;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.tron.p2p.config.Parameter;
import org.tron.p2p.connection.business.handshake.DisconnectCode;
import org.tron.p2p.connection.business.handshake.HandshakeService;
import org.tron.p2p.connection.message.HelloMessage;
import org.tron.p2p.connection.socket.MessageHandler;
import org.tron.p2p.connection.socket.TrxProtobufVarint32FrameDecoder;
import org.tron.p2p.discover.Node;
import org.tron.p2p.discover.NodeManager;
import org.tron.p2p.exception.P2pException;
import org.tron.p2p.stats.P2pStats;

@Slf4j(topic = "net")
public class Channel {

  private NodeManager nodeManager;
  private ChannelManager channelManager;

  @Setter
  private ChannelHandlerContext ctx;
  private HandshakeService handshakeService = new HandshakeService();

  private P2pStats p2pStats;
  private MessageHandler messageHandler;
  //private HandshakeHandler handshakeHandler;
  @Getter
  private volatile long disconnectTime;
  private volatile boolean isDisconnect;
  @Getter
  @Setter
  private long lastSendTime = 0;

  @Getter
  private final long startTime = System.currentTimeMillis();
  @Getter
  private boolean isActive;
  private InetSocketAddress inetSocketAddress;
  @Getter
  private Node node;

  @Getter
  @Setter
  private ByteString address;
  @Getter
  @Setter
  private volatile boolean finishHandshake;

  private boolean isTrustPeer = false;

  public Channel() {
  }

  public void init(ChannelPipeline pipeline, String remoteId, boolean discoveryMode,
      ChannelManager channelManager, NodeManager nodeManager) {

    this.channelManager = channelManager;
    this.nodeManager = nodeManager;

    this.messageHandler = new MessageHandler();

    isActive = remoteId != null && !remoteId.isEmpty();

    //TODO: use config here
    pipeline.addLast("readTimeoutHandler", new ReadTimeoutHandler(60, TimeUnit.SECONDS));
    //pipeline.addLast(stats.tcp); // todo
    pipeline.addLast("protoPender", new ProtobufVarint32LengthFieldPrepender());
    pipeline.addLast("lengthDecode", new TrxProtobufVarint32FrameDecoder(this));
    pipeline.addLast("messageDecode", messageHandler);

    messageHandler.setChannel(this);
  }

  //invoke by handshake
  public void publicHandshakeFinished(ChannelHandlerContext ctx, HelloMessage msg) {
    isTrustPeer = Parameter.p2pConfig.getTrustNodes()
        .contains((InetSocketAddress) ctx.channel().remoteAddress());

    //ctx.pipeline().remove(handshakeHandler);
    ctx.pipeline().addLast("messageCodec", messageHandler);

//    setTronState(TronState.HANDSHAKE_FINISHED);
//    getNodeStatistics().p2pHandShake.add();
    log.info("Finish handshake with {}.", ctx.channel().remoteAddress());
  }

  public void disconnect(DisconnectCode code) {
    this.isDisconnect = true;
    this.disconnectTime = System.currentTimeMillis();
    channelManager.processDisconnect(this, code);
  }

  public void channelActive(ChannelHandlerContext ctx) {
    log.info("Channel active, {}", ctx.channel().remoteAddress());
    this.ctx = ctx;
  }

  public void send(byte[] data) {
    if (isDisconnect) {
      log.warn("Send to {} failed as channel has closed, message-type:{} ",
          ctx.channel().remoteAddress(), data[0]);
      return;
    }
    ctx.writeAndFlush(data).addListener((ChannelFutureListener) future -> {
      if (!future.isSuccess() && !isDisconnect) {
        log.warn("Send to {} failed, message-type:{}",
            ctx.channel().remoteAddress(), data[0]);
      }
    });
    setLastSendTime(System.currentTimeMillis());
  }

  public void initNode(byte[] nodeId, int remotePort) {
    Node n = new Node(nodeId, inetSocketAddress.getHostString(), remotePort);
    node = nodeManager.updateNode(n);
  }

  public void handleHelloMessage(HelloMessage helloMessage) {
    handshakeService.handleHelloMsg(this, helloMessage);
  }

  public void processException(Throwable throwable) {
    Throwable baseThrowable = throwable;
    try {
      baseThrowable = Throwables.getRootCause(baseThrowable);
    } catch (IllegalArgumentException e) {
      baseThrowable = e.getCause();
      log.warn("Loop in causal chain detected");
    }
    SocketAddress address = ctx.channel().remoteAddress();
    if (throwable instanceof ReadTimeoutException
        || throwable instanceof IOException) {
      log.warn("Close peer {}, reason: {}", address, throwable.getMessage());
    } else if (baseThrowable instanceof P2pException) {
      log.warn("Close peer {}, type: {}, info: {}",
          address, ((P2pException) baseThrowable).getType(), baseThrowable.getMessage());
    } else {
      log.error("Close peer {}, exception caught", address, throwable);
    }
    close();
  }

  public boolean isDisconnect() {
    return isDisconnect;
  }

  //if handshake finished, wo have ctx
  public void setChannelHandlerContext(ChannelHandlerContext ctx) {
    this.ctx = ctx;
    this.inetSocketAddress = ctx == null ? null : (InetSocketAddress) ctx.channel().remoteAddress();
  }

  public InetAddress getInetAddress() {
    return ctx == null ? null : ((InetSocketAddress) ctx.channel().remoteAddress()).getAddress();
  }

  public String getPeerId() {
    return node == null ? "<null>" : node.getHexId();
  }

  public void close() {
    this.isDisconnect = true;
    this.disconnectTime = System.currentTimeMillis();
    ctx.close();
  }
}
