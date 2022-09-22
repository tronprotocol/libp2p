package org.tron.p2p.connection.business.handshake;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ByteToMessageDecoder;
import java.net.InetSocketAddress;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.bouncycastle.util.encoders.Hex;
import org.tron.p2p.config.Constant;
import org.tron.p2p.connection.Channel;
import org.tron.p2p.protos.Connect.HelloMessage;

@Slf4j(topic = "net")
public class HandshakeHandler extends ByteToMessageDecoder {

  private Channel channel;

  private byte[] remoteId;

  @Override
  public void channelActive(ChannelHandlerContext ctx) throws Exception {
    log.info("channel active, {}", ctx.channel().remoteAddress());
    channel.setChannelHandlerContext(ctx);
    if (remoteId.length == Constant.NODE_ID_LEN) {
      channel.initNode(remoteId, ((InetSocketAddress) ctx.channel().remoteAddress()).getPort());
      sendHelloMsg(ctx, System.currentTimeMillis());
    }
  }

  @Override
  protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) throws Exception {
    //todo
  }

  @Override
  public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
    channel.processException(cause);
  }

  public void setChannel(Channel channel, String remoteId) {
    this.channel = channel;
    this.remoteId = Hex.decode(remoteId);
  }

  protected void sendHelloMsg(ChannelHandlerContext ctx, long time) {
    //todo
  }

  private void handleHelloMsg(ChannelHandlerContext ctx, HelloMessage msg) {
    //todo
  }
}
