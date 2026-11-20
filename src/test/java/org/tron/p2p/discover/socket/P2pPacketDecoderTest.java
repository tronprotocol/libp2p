package org.tron.p2p.discover.socket;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
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
import java.util.concurrent.TimeUnit;
import org.junit.After;
import org.junit.Test;
import org.slf4j.event.Level;
import org.slf4j.event.SubstituteLoggingEvent;
import org.slf4j.helpers.MessageFormatter;
import org.slf4j.helpers.SubstituteLogger;
import org.tron.p2p.discover.message.MessageType;
import org.tron.p2p.discover.message.kad.PingMessage;
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
  private final Queue<SubstituteLoggingEvent> logs = new ArrayDeque<>();
  private final EnumSet<Level> enabledLevels = EnumSet.of(Level.INFO, Level.WARN, Level.ERROR);
  private final SubstituteLogger logger = new SubstituteLogger("net", logs, false) {
    @Override
    public boolean isInfoEnabled() {
      return enabledLevels.contains(Level.INFO);
    }

    @Override
    public boolean isWarnEnabled() {
      return enabledLevels.contains(Level.WARN);
    }

    @Override
    public boolean isErrorEnabled() {
      return enabledLevels.contains(Level.ERROR);
    }
  };
  private long now;
  private final P2pPacketDecoder decoder = new P2pPacketDecoder(logger, () -> now);
  private final EmbeddedChannel channel = new EmbeddedChannel(decoder);

  @After
  public void closeChannel() {
    channel.finishAndReleaseAll();
  }

  @Test
  public void limitsEachCategoryAcrossSendersAndReportsSuppressedCount() {
    byte[] empty = new byte[0];
    byte[] unknown = {(byte) 0xff, 0};
    assertFalse(channel.writeInbound(packet(empty, PEER)));
    assertLog(Level.WARN, BAD_LENGTH, PEER, 0);

    assertFalse(channel.writeInbound(packet(empty, OTHER_PEER)));
    assertFalse(channel.writeInbound(packet(unknown, PEER)));
    assertLog(Level.INFO, PARSE_FAILURE, -1, 2, PEER);
    now = TimeUnit.MINUTES.toNanos(1) - 1;
    assertFalse(channel.writeInbound(packet(unknown, OTHER_PEER)));
    assertFalse(channel.writeInbound(packet(empty, PEER)));
    assertTrue(logs.isEmpty());

    now++;
    assertFalse(channel.writeInbound(packet(empty, OTHER_PEER)));
    assertLog(Level.WARN, SUMMARY, "INVALID_LENGTH", 2, 4);
    assertLog(Level.WARN, BAD_LENGTH, OTHER_PEER, 0);
    assertFalse(channel.writeInbound(packet(unknown, PEER)));
    assertLog(Level.INFO, SUMMARY, "PARSE_FAILURE", 1, 3);
    assertLog(Level.INFO, PARSE_FAILURE, -1, 2, PEER);
    now += TimeUnit.MINUTES.toNanos(1);
    assertFalse(channel.writeInbound(packet(empty, PEER)));
    assertLog(Level.WARN, BAD_LENGTH, PEER, 0);
    assertTrue(logs.isEmpty());
  }

  @Test
  public void disabledWarnLeavesPendingCountsAndWindowUntouched() throws Exception {
    byte[] input = Arrays.copyOf(validPing(), 16);
    String data = ByteArray.toHexString(input);
    assertProtobufErrorPreview(input, data);
    assertFalse(channel.writeInbound(packet(input, PEER)));
    now += TimeUnit.MINUTES.toNanos(1);

    enabledLevels.remove(Level.WARN);
    assertFalse(channel.writeInbound(packet(input, PEER)));
    assertTrue(logs.isEmpty());

    enabledLevels.add(Level.WARN);
    assertFalse(channel.writeInbound(packet(input, PEER)));
    assertLog(Level.WARN, SUMMARY, "INVALID_PROTOBUF", 1, 3);
    assertLog(Level.WARN, EXCEPTION + ", cause: {}", 1, input.length, PEER, data,
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

    enabledLevels.add(Level.ERROR);
    assertFalse(channel.writeInbound(packet(unknown, PEER)));
    assertFalse(channel.writeInbound(packet(input, PEER)));
    assertLog(Level.ERROR, template, 1, input.length, PEER);
    assertFalse(channel.writeInbound(packet(input, PEER)));
    now += TimeUnit.MINUTES.toNanos(1);
    assertFalse(channel.writeInbound(packet(input, PEER)));
    assertLog(Level.ERROR, SUMMARY, "INVALID_MESSAGE", 1, 3);
    assertLog(Level.ERROR, template, 1, input.length, PEER);
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
    assertLog(Level.ERROR, "Message validation failed, type {}, len {}, address {}", 1,
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
    now += TimeUnit.MINUTES.toNanos(1);
    assertProtobufErrorPreview(Arrays.copyOf(ping, 16), hex.substring(0, 32));
    now += TimeUnit.MINUTES.toNanos(1);
    assertProtobufErrorPreview(Arrays.copyOf(ping, 17), hex.substring(0, 32) + "...");
    assertTrue(logs.isEmpty());
  }

  @Test
  public void unexpectedExceptionLogsBoundedData() throws Exception {
    byte[] ping = validPing();
    String data = ByteArray.toHexString(ping).substring(0, 32) + "...";
    enabledLevels.remove(Level.INFO);
    enabledLevels.remove(Level.WARN);
    DatagramPacket packet = packet(ping, PEER);
    try {
      // Reject the output event to exercise the unexpected exception path with a valid input.
      decoder.decode(null, packet, Collections.emptyList());
      assertLog(Level.ERROR, EXCEPTION, 1, ping.length, PEER, data);
      packet.content().resetReaderIndex();
      decoder.decode(null, packet, Collections.emptyList());
      assertTrue(logs.isEmpty());
      now += TimeUnit.MINUTES.toNanos(1);
      packet.content().resetReaderIndex();
      decoder.decode(null, packet, Collections.emptyList());
      assertLog(Level.ERROR, SUMMARY, "UNEXPECTED_ERROR", 1, 3);
      assertLog(Level.ERROR, EXCEPTION, 1, ping.length, PEER, data);
      assertTrue(logs.isEmpty());
    } finally {
      packet.release();
    }
  }

  private void assertProtobufErrorPreview(byte[] input, String expectedData) {
    assertFalse(channel.writeInbound(packet(input, PEER)));
    assertLog(Level.WARN, EXCEPTION + ", cause: {}", 1, input.length, PEER, expectedData,
        "InvalidProtocolBufferException");
  }

  private void assertLog(Level level, String template, Object... arguments) {
    SubstituteLoggingEvent event = logs.remove();
    assertEquals("net", event.getLoggerName());
    assertEquals(level, event.getLevel());
    assertNull(event.getThrowable());
    assertEquals(template, event.getMessage());
    assertEquals(arguments.length, event.getArgumentArray().length);
    assertEquals(MessageFormatter.arrayFormat(template, arguments).getMessage(),
        MessageFormatter.arrayFormat(event.getMessage(), event.getArgumentArray()).getMessage());
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
