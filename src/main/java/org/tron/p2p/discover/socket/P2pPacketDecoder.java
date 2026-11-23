package org.tron.p2p.discover.socket;

import com.google.protobuf.InvalidProtocolBufferException;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.socket.DatagramPacket;
import io.netty.handler.codec.MessageToMessageDecoder;
import java.util.EnumMap;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.LongSupplier;
import java.util.function.Predicate;
import lombok.extern.slf4j.Slf4j;
import org.bouncycastle.util.encoders.Hex;
import org.slf4j.Logger;
import org.tron.p2p.base.Constant;
import org.tron.p2p.discover.message.Message;
import org.tron.p2p.exception.P2pException;

@Slf4j(topic = "net")
public class P2pPacketDecoder extends MessageToMessageDecoder<DatagramPacket> {

  private static final long LOG_INTERVAL_NANOS = TimeUnit.MINUTES.toNanos(1);
  private static final int MAX_LOG_DATA_BYTES = 16;

  private final LongSupplier nanoTime;
  // Each decoder belongs to one channel. Only that channel's event loop updates these counters.
  private final EnumMap<DropReason, DropCounter> drops = new EnumMap<>(DropReason.class);

  public P2pPacketDecoder() {
    this(System::nanoTime);
  }

  P2pPacketDecoder(LongSupplier nanoTime) {
    this.nanoTime = nanoTime;
  }

  @Override
  public void decode(ChannelHandlerContext ctx, DatagramPacket packet, List<Object> out)
      throws Exception {
    ByteBuf buf = packet.content();
    int length = buf.readableBytes();
    if (length <= 1 || length >= Constant.MAX_DISCOVERY_PACKET_SIZE) {
      if (shouldLog(DropReason.INVALID_LENGTH)) {
        log.warn("UDP rcv bad packet, from {} length = {}", packet.sender(), length);
      }
      return;
    }
    byte[] encoded = new byte[length];
    buf.readBytes(encoded);
    try {
      UdpEvent event = new UdpEvent(Message.parse(encoded), packet.sender());
      out.add(event);
    } catch (P2pException pe) {
      if (pe.getType() == P2pException.TypeEnum.BAD_MESSAGE) {
        if (shouldLog(DropReason.INVALID_MESSAGE)) {
          log.error("Message validation failed, type {}, len {}, address {}", encoded[0],
              length, packet.sender());
        }
      } else if (shouldLog(DropReason.PARSE_FAILURE)) {
        log.info("Parse msg failed, type {}, len {}, address {}", encoded[0], length,
            packet.sender());
      }
    } catch (InvalidProtocolBufferException e) {
      if (shouldLog(DropReason.INVALID_PROTOBUF)) {
        log.warn("An exception occurred while parsing the message, type {}, len {}, address {}, "
                + "data {}, cause: {}", encoded[0], length, packet.sender(), dataPreview(encoded),
            e.getClass().getSimpleName());
      }
    } catch (Exception e) {
      if (shouldLog(DropReason.UNEXPECTED_ERROR)) {
        log.error("An exception occurred while parsing the message, type {}, len {}, address {}, "
                + "data {}", encoded[0], length, packet.sender(), dataPreview(encoded));
      }
    }
  }

  private static String dataPreview(byte[] encoded) {
    int length = Integer.min(encoded.length, MAX_LOG_DATA_BYTES);
    String hex = Hex.toHexString(encoded, 0, length);
    return length < encoded.length ? hex + "..." : hex;
  }

  private boolean shouldLog(DropReason reason) {
    if (!reason.enabled.test(log)) {
      return false;
    }
    DropCounter counter = drops.computeIfAbsent(reason, ignored -> new DropCounter());
    long now = nanoTime.getAsLong();
    counter.total++;
    if (counter.lastReportedTotal != 0 && now - counter.lastLogTime < LOG_INTERVAL_NANOS) {
      return false;
    }
    long suppressed = counter.total - counter.lastReportedTotal - 1;
    counter.lastReportedTotal = counter.total;
    counter.lastLogTime = now;
    if (suppressed > 0) {
      // Keep counts on a separate line so existing packet log parsers still match.
      reason.summary.log(log, "Suppressed UDP discovery logs: reason={}, suppressed={}, total={}",
          reason, suppressed, counter.total);
    }
    return true;
  }

  private enum DropReason {
    INVALID_LENGTH(Logger::isWarnEnabled, Logger::warn),
    INVALID_MESSAGE(Logger::isErrorEnabled, Logger::error),
    PARSE_FAILURE(Logger::isInfoEnabled, Logger::info),
    INVALID_PROTOBUF(Logger::isWarnEnabled, Logger::warn),
    UNEXPECTED_ERROR(Logger::isErrorEnabled, Logger::error);

    private final Predicate<Logger> enabled;
    private final SummaryLogger summary;

    DropReason(Predicate<Logger> enabled, SummaryLogger summary) {
      this.enabled = enabled;
      this.summary = summary;
    }
  }

  @FunctionalInterface
  private interface SummaryLogger {
    void log(Logger logger, String template, Object... arguments);
  }

  private static final class DropCounter {
    private long total;
    private long lastReportedTotal;
    private long lastLogTime;
  }
}
