package org.tron.p2p.discover.socket;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.google.protobuf.ByteString;
import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.channel.socket.DatagramPacket;
import java.net.InetSocketAddress;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Queue;
import org.junit.After;
import org.junit.Test;
import org.tron.p2p.discover.message.MessageType;
import org.tron.p2p.discover.message.kad.PingMessage;
import org.tron.p2p.discover.socket.P2pPacketDecoder.DropCounter;
import org.tron.p2p.discover.socket.P2pPacketDecoder.DropReason;
import org.tron.p2p.discover.socket.P2pPacketDecoder.LogLevel;
import org.tron.p2p.protos.Discover;
import org.tron.p2p.utils.ByteArray;

public class P2pPacketDecoderTest {

  private static final String BAD_LENGTH = "UDP rcv bad packet, from {} length = {}";
  private static final String PARSE_FAILURE = "Parse msg failed, type {}, len {}, address {}";
  private static final String EXCEPTION = "An exception occurred while parsing the message, "
      + "type {}, len {}, address {}, data {}";
  private static final String SUMMARY =
      "Suppressed UDP discovery logs: reason={}, suppressed={}, total={}";
  private static final InetSocketAddress LOCAL = new InetSocketAddress("127.0.0.1", 18888);
  private static final InetSocketAddress PEER = new InetSocketAddress("127.0.0.1", 18889);
  private static final InetSocketAddress OTHER_PEER = new InetSocketAddress("127.0.0.2", 18888);
  private final Queue<LogEntry> logs = new ArrayDeque<>();
  private final EnumSet<LogLevel> enabledLevels = EnumSet.allOf(LogLevel.class);
  private final EnumSet<DropReason> usedPermits = EnumSet.noneOf(DropReason.class);
  private final P2pPacketDecoder decoder = new P2pPacketDecoder(reason -> new DropCounter(
      () -> usedPermits.add(reason), () -> enabledLevels.contains(reason.level),
      (template, arguments) -> logs.add(new LogEntry(reason.level, template, arguments))));
  private final EmbeddedChannel channel = new EmbeddedChannel(decoder);

  @After
  public void tearDown() {
    channel.finishAndReleaseAll();
  }

  @Test
  public void invalidLengthSampleUsesLengthAtDecodeEntry() throws Exception {
    DatagramPacket input = packet(new byte[] {1, 2}, PEER);
    try {
      input.content().skipBytes(1);
      P2pPacketDecoder readingDecoder = new P2pPacketDecoder(reason -> new DropCounter(() -> {
        // Consume the last byte between the length check and the sample write.
        input.content().skipBytes(1);
        return true;
      }, () -> true, (template, arguments) ->
          logs.add(new LogEntry(reason.level, template, arguments))));
      readingDecoder.decode(null, input, Collections.emptyList());
      assertEquals(0, input.content().readableBytes());
      assertLog(LogLevel.WARN, BAD_LENGTH, PEER, 1);
    } finally {
      input.release();
    }
  }

  @Test
  public void limitsEachCategoryAcrossSendersAndReportsSuppressedCount() {
    byte[] empty = new byte[0];
    byte[] unknown = {(byte) 0xff, 0};
    assertFalse(channel.writeInbound(packet(empty, PEER)));
    assertLog(LogLevel.WARN, BAD_LENGTH, PEER, 0);

    assertFalse(channel.writeInbound(packet(empty, OTHER_PEER)));
    assertFalse(channel.writeInbound(packet(unknown, PEER)));
    assertLog(LogLevel.INFO, PARSE_FAILURE, -1, 2, PEER);
    assertFalse(channel.writeInbound(packet(unknown, OTHER_PEER)));
    assertFalse(channel.writeInbound(packet(empty, PEER)));
    assertTrue(logs.isEmpty());

    usedPermits.clear();
    assertFalse(channel.writeInbound(packet(empty, OTHER_PEER)));
    assertLog(LogLevel.WARN, SUMMARY, "INVALID_LENGTH", 2, 4);
    assertLog(LogLevel.WARN, BAD_LENGTH, OTHER_PEER, 0);
    assertFalse(channel.writeInbound(packet(unknown, PEER)));
    assertLog(LogLevel.INFO, SUMMARY, "PARSE_FAILURE", 1, 3);
    assertLog(LogLevel.INFO, PARSE_FAILURE, -1, 2, PEER);
    usedPermits.clear();
    assertFalse(channel.writeInbound(packet(empty, PEER)));
    assertLog(LogLevel.WARN, BAD_LENGTH, PEER, 0);
    assertTrue(logs.isEmpty());

    assertFalse(channel.writeInbound(packet(empty, OTHER_PEER)));
    usedPermits.clear();
    assertFalse(channel.writeInbound(packet(empty, PEER)));
    assertLog(LogLevel.WARN, SUMMARY, "INVALID_LENGTH", 1, 7);
    assertLog(LogLevel.WARN, BAD_LENGTH, PEER, 0);
    assertTrue(logs.isEmpty());
  }

  @Test
  public void disabledWarnPreservesPendingCountsAndAvailablePermit() throws Exception {
    byte[] input = Arrays.copyOf(validPing(), 16);
    String data = ByteArray.toHexString(input);
    assertProtobufErrorPreview(input, data);
    assertFalse(channel.writeInbound(packet(input, PEER)));
    usedPermits.clear();

    enabledLevels.remove(LogLevel.WARN);
    assertFalse(channel.writeInbound(packet(input, PEER)));
    assertTrue(logs.isEmpty());

    enabledLevels.add(LogLevel.WARN);
    assertFalse(channel.writeInbound(packet(input, PEER)));
    assertLog(LogLevel.WARN, SUMMARY, "INVALID_PROTOBUF", 1, 3);
    assertLog(LogLevel.WARN, EXCEPTION + ", cause: {}", 1, input.length, PEER, data,
        "InvalidProtocolBufferException");
    assertTrue(logs.isEmpty());
  }

  @Test
  public void errorOnlyLoggingReportsValidationSamplesAndCountsAtError() throws Exception {
    byte[] input = new PingMessage(Discover.PingMessage.newBuilder()
        .setTimestamp(1).build().toByteArray()).getSendData();
    String template = "Message validation failed, type {}, len {}, address {}";
    byte[] unknown = {(byte) 0xff, 0};
    enabledLevels.clear();
    assertFalse(channel.writeInbound(packet(input, PEER)));
    assertTrue(logs.isEmpty());

    enabledLevels.add(LogLevel.ERROR);
    assertFalse(channel.writeInbound(packet(unknown, PEER)));
    assertFalse(channel.writeInbound(packet(input, PEER)));
    assertLog(LogLevel.ERROR, template, 1, input.length, PEER);
    assertFalse(channel.writeInbound(packet(input, PEER)));
    usedPermits.clear();
    assertFalse(channel.writeInbound(packet(input, PEER)));
    assertLog(LogLevel.ERROR, SUMMARY, "INVALID_MESSAGE", 1, 3);
    assertLog(LogLevel.ERROR, template, 1, input.length, PEER);
    assertTrue(logs.isEmpty());
  }

  @Test
  public void logsBoundedDataForDecodeErrorsAndForwardsValidPing() throws Exception {
    byte[] ping = validPing();
    byte[] incomplete = Arrays.copyOf(ping, ping.length - 1);
    byte[] missingEndpoint = new PingMessage(Discover.PingMessage.newBuilder()
        .setTimestamp(1).build().toByteArray()).getSendData();

    assertProtobufErrorPreview(incomplete,
        ByteArray.toHexString(incomplete).substring(0, 32) + "...");
    assertFalse(channel.writeInbound(packet(incomplete, OTHER_PEER)));
    assertFalse(channel.writeInbound(packet(missingEndpoint, PEER)));
    assertLog(LogLevel.ERROR, "Message validation failed, type {}, len {}, address {}", 1,
        missingEndpoint.length, PEER);
    assertFalse(channel.writeInbound(packet(missingEndpoint, OTHER_PEER)));

    assertTrue(channel.writeInbound(packet(ping, PEER)));
    UdpEvent event = channel.readInbound();
    assertEquals(PEER, event.getAddress());
    assertEquals(MessageType.KAD_PING, event.getMessage().getType());
    assertArrayEquals(ping, event.getMessage().getSendData());
    assertTrue(logs.isEmpty());
  }

  @Test
  public void addsEllipsisOnlyWhenDataExceedsSixteenBytes() throws Exception {
    byte[] ping = validPing();
    String hex = ByteArray.toHexString(ping);
    assertProtobufErrorPreview(Arrays.copyOf(ping, 15), hex.substring(0, 30));
    usedPermits.clear();
    assertProtobufErrorPreview(Arrays.copyOf(ping, 16), hex.substring(0, 32));
    usedPermits.clear();
    assertProtobufErrorPreview(Arrays.copyOf(ping, 17), hex.substring(0, 32) + "...");
    assertTrue(logs.isEmpty());
  }

  @Test
  public void unexpectedExceptionLogsBoundedData() throws Exception {
    byte[] ping = validPing();
    String data = ByteArray.toHexString(ping).substring(0, 32) + "...";
    enabledLevels.remove(LogLevel.INFO);
    enabledLevels.remove(LogLevel.WARN);
    DatagramPacket packet = packet(ping, PEER);
    try {
      // Reject the output event to exercise the unexpected exception path with a valid input.
      decoder.decode(null, packet, Collections.emptyList());
      assertLog(LogLevel.ERROR, EXCEPTION, 1, ping.length, PEER, data);
      packet.content().resetReaderIndex();
      decoder.decode(null, packet, Collections.emptyList());
      assertTrue(logs.isEmpty());
      usedPermits.clear();
      packet.content().resetReaderIndex();
      decoder.decode(null, packet, Collections.emptyList());
      assertLog(LogLevel.ERROR, SUMMARY, "UNEXPECTED_ERROR", 1, 3);
      assertLog(LogLevel.ERROR, EXCEPTION, 1, ping.length, PEER, data);
      assertTrue(logs.isEmpty());
    } finally {
      packet.release();
    }
  }

  private void assertProtobufErrorPreview(byte[] input, String expectedData) {
    assertFalse(channel.writeInbound(packet(input, PEER)));
    assertLog(LogLevel.WARN, EXCEPTION + ", cause: {}", 1, input.length, PEER, expectedData,
        "InvalidProtocolBufferException");
  }

  private void assertLog(LogLevel level, String template, Object... arguments) {
    assertFalse("Expected a log event for " + template, logs.isEmpty());
    LogEntry entry = logs.remove();
    assertEquals(level, entry.level);
    assertEquals(template, entry.template);
    assertEquals(arguments.length, entry.arguments.length);
    for (int i = 0; i < arguments.length; i++) {
      assertEquals("argument " + i, String.valueOf(arguments[i]),
          String.valueOf(entry.arguments[i]));
    }
  }

  private static final class LogEntry {
    final LogLevel level;
    final String template;
    final Object[] arguments;

    LogEntry(LogLevel level, String template, Object[] arguments) {
      this.level = level;
      this.template = template;
      this.arguments = arguments;
    }
  }

  private static byte[] validPing() throws Exception {
    Discover.Endpoint from = Discover.Endpoint.newBuilder()
        .setNodeId(ByteString.copyFrom(new byte[64])).setAddress(ByteString.copyFromUtf8("127.0.0.1"))
        .setPort(PEER.getPort()).build();
    return new PingMessage(Discover.PingMessage.newBuilder().setFrom(from).build()
        .toByteArray()).getSendData();
  }

  private static DatagramPacket packet(byte[] bytes, InetSocketAddress sender) {
    return new DatagramPacket(Unpooled.wrappedBuffer(bytes), LOCAL, sender);
  }
}
