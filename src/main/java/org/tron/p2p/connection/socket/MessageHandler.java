package org.tron.p2p.connection.socket;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ByteToMessageDecoder;
import lombok.extern.slf4j.Slf4j;
import org.tron.p2p.P2pEventHandler;
import org.tron.p2p.config.Parameter;
import org.tron.p2p.connection.Channel;
import org.tron.p2p.connection.message.HelloMessage;
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
  protected void decode(ChannelHandlerContext ctx, ByteBuf buffer, List<Object> out) throws Exception {
    byte[] data = new byte[buffer.readableBytes()];
    buffer.readBytes(data);
    byte type = data[0];
    switch (type) {
      case Message.PING:
        break;
      case Message.PONG:
        break;
      case Message.HELLO:
        channel.handleHelloMessage(new HelloMessage(data));
        break;
      default:
        handMessage(channel, data);
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

  private void handMessage(Channel channel, byte[] data) {
    P2pEventHandler handler = Parameter.handlerMap.get(data[0]);
    if (handler != null) {
      if (!channel.isFinishHandshake()) {
        channel.setFinishHandshake(true);
        Parameter.handlerList.forEach(h -> h.onConnect(channel));
      }
      handler.onMessage(channel, data);
    } else {
      log.warn("Receive bad message from {}, type:{}",
              channel.getInetAddress(), data[0]);
      channel.close();
    }
  }
}