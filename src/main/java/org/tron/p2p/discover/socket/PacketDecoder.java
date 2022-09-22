package org.tron.p2p.discover.socket;

import java.util.List;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.socket.DatagramPacket;
import io.netty.handler.codec.MessageToMessageDecoder;
import lombok.extern.slf4j.Slf4j;
import org.tron.p2p.discover.socket.message.Message;

@Slf4j(topic = "discover")
public class PacketDecoder extends MessageToMessageDecoder<DatagramPacket> {

  private static final int MAXSIZE = 2048;

  @Override
  public void decode(ChannelHandlerContext ctx, DatagramPacket packet, List<Object> out)
      throws Exception {
    ByteBuf buf = packet.content();
    int length = buf.readableBytes();
    if (length <= 1 || length >= MAXSIZE) {
      log.warn("UDP rcv bad packet, from {} length = {}", ctx.channel().remoteAddress(), length);
      return;
    }
    byte[] encoded = new byte[length];
    buf.readBytes(encoded);
    try {
      UdpEvent event = new UdpEvent(Message.parse(encoded), packet.sender());
      out.add(event);
    } catch (Exception e) {
      log.error("Parse msg failed, type {}, len {}, address {}", encoded[0], encoded.length,
          packet.sender());
    }
  }
}
