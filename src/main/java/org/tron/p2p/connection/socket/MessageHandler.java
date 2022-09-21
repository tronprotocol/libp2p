package org.tron.p2p.connection.socket;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ByteToMessageDecoder;
import java.util.List;

import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.tron.p2p.P2pEventHandler;
import org.tron.p2p.config.Parameter;
import org.tron.p2p.connection.Channel;
import org.tron.p2p.connection.message.HelloMessage;
import org.tron.p2p.connection.message.Message;
import org.tron.p2p.connection.message.TcpPongMessage;
import org.tron.p2p.exception.P2pException;
import org.tron.p2p.exception.P2pException.TypeEnum;

@Slf4j(topic = "net")
public class MessageHandler extends ByteToMessageDecoder {

  @Setter
  private Channel channel;

  @Override
  public void handlerAdded(ChannelHandlerContext ctx) {
  }

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
    try {
      switch (type) {
        case Message.PING:
          //todo statistic
          channel.send(new TcpPongMessage().getData());
          break;
        case Message.PONG:
          //todo statistic
          break;
        case Message.HELLO:
          try {
            HelloMessage helloMessage = new HelloMessage(encoded);
            //todo handshake
          } catch (Exception e) {
            throw new P2pException(TypeEnum.PARSE_MESSAGE_FAILED, e);
          }
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
    } catch (Exception e) {
      channel.processException(e);
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