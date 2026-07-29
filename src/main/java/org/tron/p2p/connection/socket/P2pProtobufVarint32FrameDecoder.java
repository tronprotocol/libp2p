package org.tron.p2p.connection.socket;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ByteToMessageDecoder;
import io.netty.handler.codec.CorruptedFrameException;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.tron.p2p.base.Parameter;
import org.tron.p2p.connection.Channel;
import org.tron.p2p.connection.message.base.P2pDisconnectMessage;
import org.tron.p2p.protos.Connect.DisconnectReason;

@Slf4j(topic = "net")
public class P2pProtobufVarint32FrameDecoder extends ByteToMessageDecoder {

  private final Channel channel;

  public P2pProtobufVarint32FrameDecoder(Channel channel) {
    this.channel = channel;
  }

  private static int readRawVarint32(ByteBuf buffer) {
    if (!buffer.isReadable()) {
      return 0;
    }
    buffer.markReaderIndex();
    byte tmp = buffer.readByte();
    if (tmp >= 0) {
      return tmp;
    } else {
      int result = tmp & 127;
      if (!buffer.isReadable()) {
        buffer.resetReaderIndex();
        return 0;
      }
      if ((tmp = buffer.readByte()) >= 0) {
        result |= tmp << 7;
      } else {
        result |= (tmp & 127) << 7;
        if (!buffer.isReadable()) {
          buffer.resetReaderIndex();
          return 0;
        }
        if ((tmp = buffer.readByte()) >= 0) {
          result |= tmp << 14;
        } else {
          result |= (tmp & 127) << 14;
          if (!buffer.isReadable()) {
            buffer.resetReaderIndex();
            return 0;
          }
          if ((tmp = buffer.readByte()) >= 0) {
            result |= tmp << 21;
          } else {
            result |= (tmp & 127) << 21;
            if (!buffer.isReadable()) {
              buffer.resetReaderIndex();
              return 0;
            }
            result |= (tmp = buffer.readByte()) << 28;
            if (tmp < 0) {
              throw new CorruptedFrameException("malformed varint.");
            }
          }
        }
      }
      return result;
    }
  }

  @Override
  protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) {
    // Evict a still-un-handshaked inbound connection once it passes the handshake deadline.
    // A slow-trickle peer keeps this connection alive by sending bytes; every such byte lands
    // here, so its own keep-alive traffic triggers the eviction (no timer / no polling needed).
    if (!channel.isActive() && !channel.isFinishHandshake() && !channel.isTrustPeer()
        && System.currentTimeMillis() - channel.getStartTime() > Parameter.HANDSHAKE_TIMEOUT_MS) {
      log.info("Close pending peer {}, handshake not finished within {} ms",
          ctx.channel().remoteAddress(), Parameter.HANDSHAKE_TIMEOUT_MS);
      in.clear();
      channel.close();
      return;
    }
    in.markReaderIndex();
    int preIndex = in.readerIndex();
    int length = readRawVarint32(in);
    // Before the handshake completes, only a tiny HELLO is expected. Cap the accepted frame
    // length so an un-handshaked peer cannot make the decoder buffer up to ~5 MB per connection.
    int maxLength = channel.isFinishHandshake()
        ? Parameter.MAX_MESSAGE_LENGTH : Parameter.MAX_PRE_HANDSHAKE_LENGTH;
    if (length >= maxLength) {
      log.warn("Receive a big msg or not encoded msg, host : {}, msg length is : {}, "
              + "finishHandshake : {}", ctx.channel().remoteAddress(), length,
          channel.isFinishHandshake());
      in.clear();
      channel.send(new P2pDisconnectMessage(DisconnectReason.BAD_MESSAGE));
      channel.close();
      return;
    }
    if (preIndex == in.readerIndex()) {
      return;
    }
    if (length < 0) {
      throw new CorruptedFrameException("negative length: " + length);
    }

    if (in.readableBytes() < length) {
      in.resetReaderIndex();
    } else {
      out.add(in.readRetainedSlice(length));
    }
  }
}
