package org.tron.p2p.discover.socket;

import com.google.common.util.concurrent.RateLimiter;
import com.google.protobuf.InvalidProtocolBufferException;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.socket.DatagramPacket;
import io.netty.handler.codec.MessageToMessageDecoder;
import java.net.InetSocketAddress;
import java.util.EnumMap;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Function;
import lombok.extern.slf4j.Slf4j;
import org.bouncycastle.util.encoders.Hex;
import org.tron.p2p.base.Constant;
import org.tron.p2p.discover.message.Message;
import org.tron.p2p.exception.P2pException;

@Slf4j(topic = "net")
public class P2pPacketDecoder extends MessageToMessageDecoder<DatagramPacket> {

  private static final double SAMPLES_PER_SECOND = 1.0 / 60;
  private static final int MAX_LOG_DATA_BYTES = 16;

  // Each decoder belongs to one channel. Only that channel's event loop updates these counters.
  private final EnumMap<DropReason, DropCounter> drops = new EnumMap<>(DropReason.class);

  public P2pPacketDecoder() {
    this(reason -> new DropCounter(RateLimiter.create(SAMPLES_PER_SECOND)::tryAcquire,
        reason.level.enabled, reason.level.write));
  }

  P2pPacketDecoder(Function<DropReason, DropCounter> newCounter) {
    for (DropReason reason : DropReason.values()) {
      drops.put(reason, newCounter.apply(reason));
    }
  }

  @Override
  public void decode(ChannelHandlerContext ctx, DatagramPacket packet, List<Object> out)
      throws Exception {
    ByteBuf buf = packet.content();
    int length = buf.readableBytes();
    if (length <= 1 || length >= Constant.MAX_DISCOVERY_PACKET_SIZE) {
      logDrop(DropReason.INVALID_LENGTH, packet, length, null, null);
      return;
    }
    byte[] encoded = new byte[length];
    buf.readBytes(encoded);
    try {
      UdpEvent event = new UdpEvent(Message.parse(encoded), packet.sender());
      out.add(event);
    } catch (P2pException pe) {
      DropReason reason = pe.getType() == P2pException.TypeEnum.BAD_MESSAGE
          ? DropReason.INVALID_MESSAGE : DropReason.PARSE_FAILURE;
      logDrop(reason, packet, length, encoded, pe);
    } catch (InvalidProtocolBufferException e) {
      logDrop(DropReason.INVALID_PROTOBUF, packet, length, encoded, e);
    } catch (Exception e) {
      logDrop(DropReason.UNEXPECTED_ERROR, packet, length, encoded, e);
    }
  }

  private static String dataPreview(byte[] encoded) {
    int length = Integer.min(encoded.length, MAX_LOG_DATA_BYTES);
    String hex = Hex.toHexString(encoded, 0, length);
    return length < encoded.length ? hex + "..." : hex;
  }

  private void logDrop(DropReason reason, DatagramPacket packet, int length, byte[] encoded,
      Exception cause) {
    DropCounter counter = drops.get(reason);
    if (!counter.enabled.getAsBoolean()) {
      return;
    }
    counter.totalWhileEnabled++;
    if (!counter.tryAcquire.getAsBoolean()) {
      counter.suppressed++;
      return;
    }
    counter.reportSuppressed(reason);
    reason.sample.write(counter.write, packet.sender(), length, encoded, cause);
  }

  enum LogLevel {
    INFO(log::isInfoEnabled, log::info),
    WARN(log::isWarnEnabled, log::warn),
    ERROR(log::isErrorEnabled, log::error);

    private final BooleanSupplier enabled;
    private final LogWriter write;

    LogLevel(BooleanSupplier enabled, LogWriter write) {
      this.enabled = enabled;
      this.write = write;
    }
  }

  enum DropReason {
    INVALID_LENGTH(LogLevel.WARN, (out, sender, length, data, cause) ->
        out.log("UDP rcv bad packet, from {} length = {}", sender, length)),
    INVALID_MESSAGE(LogLevel.ERROR, (out, sender, length, data, cause) ->
        out.log("Message validation failed, type {}, len {}, address {}", data[0], length, sender)),
    PARSE_FAILURE(LogLevel.INFO, (out, sender, length, data, cause) ->
        out.log("Parse msg failed, type {}, len {}, address {}", data[0], length, sender)),
    INVALID_PROTOBUF(LogLevel.WARN, (out, sender, length, data, cause) ->
        out.log("An exception occurred while parsing the message, type {}, len {}, address {}, "
                + "data {}, cause: {}", data[0], length, sender, dataPreview(data),
            cause.getClass().getSimpleName())),
    UNEXPECTED_ERROR(LogLevel.ERROR, (out, sender, length, data, cause) ->
        out.log("An exception occurred while parsing the message, type {}, len {}, address {}, "
            + "data {}", data[0], length, sender, dataPreview(data)));

    final LogLevel level;
    private final SampleWriter sample;

    DropReason(LogLevel level, SampleWriter sample) {
      this.level = level;
      this.sample = sample;
    }
  }

  @FunctionalInterface
  interface LogWriter {
    void log(String template, Object... arguments);
  }

  @FunctionalInterface
  private interface SampleWriter {
    void write(LogWriter output, InetSocketAddress sender, int length, byte[] encoded,
        Exception cause);
  }

  static final class DropCounter {
    private final BooleanSupplier tryAcquire;
    private final BooleanSupplier enabled;
    private final LogWriter write;
    private long totalWhileEnabled;
    private long suppressed;

    DropCounter(BooleanSupplier tryAcquire, BooleanSupplier enabled, LogWriter write) {
      this.tryAcquire = tryAcquire;
      this.enabled = enabled;
      this.write = write;
    }

    private void reportSuppressed(DropReason reason) {
      if (suppressed > 0) {
        write.log("Suppressed UDP discovery logs: reason={}, suppressed={}, total={}",
            reason, suppressed, totalWhileEnabled);
        suppressed = 0;
      }
    }
  }
}
