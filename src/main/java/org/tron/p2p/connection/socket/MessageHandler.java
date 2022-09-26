package org.tron.p2p.connection.socket;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ByteToMessageDecoder;
import lombok.extern.slf4j.Slf4j;
import org.tron.p2p.connection.Channel;
import org.tron.p2p.connection.ChannelManager;

import java.util.List;

@Slf4j(topic = "net")
public class MessageHandler extends ByteToMessageDecoder {
  private Channel channel;

  public MessageHandler(Channel channel) {
    this.channel = channel;
  }

  @Override
  public void handlerAdded(ChannelHandlerContext ctx) {}

  @Override
  public void channelActive(ChannelHandlerContext ctx) {
    log.info("Channel active, {}", ctx.channel().remoteAddress());
    channel.setChannelHandlerContext(ctx);
    if (channel.isActive()) {
      ChannelManager.getHandshakeService().startHandshake(channel);
    }
  }

  @Override
  protected void decode(ChannelHandlerContext ctx, ByteBuf buffer, List<Object> out) {
    byte[] data = new byte[buffer.readableBytes()];
    buffer.readBytes(data);
    try {
      ChannelManager.processMessage(channel, data);
    } catch (Exception e) {
      channel.processException(e);
    }
  }

  @Override
  public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
    channel.processException(cause);
  }

}