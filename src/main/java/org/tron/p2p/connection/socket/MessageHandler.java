package org.tron.p2p.connection.socket;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ByteToMessageDecoder;
import lombok.extern.slf4j.Slf4j;
import org.tron.p2p.P2pEventHandler;
import org.tron.p2p.config.Parameter;
import org.tron.p2p.connection.Channel;
import org.tron.p2p.connection.message.Message;

import java.util.List;

@Slf4j(topic = "net")
public class MessageHandler extends ByteToMessageDecoder {

  private Channel channel;

  @Override
  public void handlerAdded(ChannelHandlerContext ctx) {}

  @Override
  public void channelActive(ChannelHandlerContext ctx) {
    log.info("Channel active, {}", ctx.channel().remoteAddress());
    channel.setCtx(ctx);
  }

  @Override
  protected void decode(ChannelHandlerContext ctx, ByteBuf buffer, List<Object> out) {
    byte[] encoded = new byte[buffer.readableBytes()];
    buffer.readBytes(encoded);
    byte type = encoded[0];
    switch (type) {
      case Message.PING:
        break;
      case Message.PONG:
        break;
      case Message.HELLO:
        break;
      default:
        P2pEventHandler handler = Parameter.handlerMap.get(type);
        if (handler != null) {
          handler.onMessage(channel, encoded);
        } else {
          log.warn("Receive bab message from {}, type:{}",
                  ctx.channel().remoteAddress(), type);
          channel.close();
        }
        break;
    }
  }

  @Override
  public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
    channel.processException(cause);
  }

  public void setChannel(Channel channel) {
    this.channel = channel;
  }

}