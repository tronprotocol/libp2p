package org.tron.p2p.discover.protocol.kad;

import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.channel.socket.DatagramPacket;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.tron.p2p.P2pConfig;
import org.tron.p2p.base.Parameter;
import org.tron.p2p.discover.Node;
import org.tron.p2p.discover.message.MessageType;
import org.tron.p2p.discover.message.kad.FindNodeMessage;
import org.tron.p2p.discover.socket.MessageHandler;
import org.tron.p2p.discover.socket.P2pPacketDecoder;

public class PongTimerTest {

  private static P2pConfig config;
  private P2pConfig previousConfig;
  private long previousTimeout;
  private KadService service;
  private ScheduledThreadPoolExecutor timer;

  @BeforeClass
  public static void createConfig() {
    config = new P2pConfig();
    config.setIp("127.0.0.1");
    config.setIpv6("");
  }

  @Before
  public void setUp() {
    previousConfig = Parameter.p2pConfig;
    previousTimeout = KadService.getPingTimeout();
    Parameter.p2pConfig = config;
    config.setDiscoverEnable(false);
    KadService.setPingTimeout(60_000);
    service = new KadService();
    service.init();
    timer = (ScheduledThreadPoolExecutor) service.getPongTimer();
  }

  @After
  public void tearDown() {
    service.close();
    KadService.setPingTimeout(previousTimeout);
    Parameter.p2pConfig = previousConfig;
  }

  @Test(timeout = 20000)
  public void fullQueueRejectsAndAcceptsAgainAfterTaskExecutes() throws Exception {
    CountDownLatch started = new CountDownLatch(1);
    CountDownLatch release = new CountDownLatch(1);
    timer.execute(() -> {
      started.countDown();
      try {
        release.await();
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
      }
    });
    try {
      Assert.assertTrue(started.await(5, TimeUnit.SECONDS));
      Future<?> ready = timer.schedule(() -> { }, 0, TimeUnit.SECONDS);
      for (int i = 1; i < KadService.MAX_PENDING_PONG_TASKS; i++) {
        submitDelayedTask();
      }
      Assert.assertThrows(RejectedExecutionException.class, this::submitDelayedTask);
      Assert.assertEquals(KadService.MAX_PENDING_PONG_TASKS, timer.getQueue().size());
      release.countDown();
      ready.get(5, TimeUnit.SECONDS);
      submitDelayedTask();
      Assert.assertEquals(KadService.MAX_PENDING_PONG_TASKS, timer.getQueue().size());
    } finally {
      release.countDown();
    }
  }

  @Test(timeout = 20000)
  public void concurrentSubmissionsRespectQueueLimit() throws Exception {
    ExecutorService executor = Executors.newFixedThreadPool(8);
    CountDownLatch start = new CountDownLatch(1);
    List<Future<Integer>> results = new ArrayList<>();
    try {
      for (int i = 0; i < 8; i++) {
        results.add(executor.submit(() -> {
          Assert.assertTrue(start.await(5, TimeUnit.SECONDS));
          int accepted = 0;
          for (int j = 0; j < 300; j++) {
            try {
              submitDelayedTask();
              accepted++;
            } catch (RejectedExecutionException expected) {
              // A full timer must reject without exceeding its queue limit.
            }
          }
          return accepted;
        }));
      }
      start.countDown();
      int accepted = 0;
      for (Future<Integer> result : results) {
        accepted += result.get(10, TimeUnit.SECONDS);
      }
      Assert.assertEquals(KadService.MAX_PENDING_PONG_TASKS, accepted);
      Assert.assertEquals(accepted, timer.getQueue().size());
    } finally {
      executor.shutdownNow();
      Assert.assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS));
    }
  }

  @Test
  public void overflowDoesNotCloseUdpChannelOrChangeNodeProcessing() {
    AtomicInteger pings = new AtomicInteger();
    config.setDiscoverEnable(true);
    service.setMessageSender(event -> {
      if (event.getMessage().getType() == MessageType.KAD_PING) {
        pings.incrementAndGet();
      }
    });
    EmbeddedChannel pipeline =
        new EmbeddedChannel(new P2pPacketDecoder(), new MessageHandler(null, service));
    Node from = new Node(new byte[64], "127.0.0.2", "", 18888);
    byte[] wire = new FindNodeMessage(from, new byte[64]).getSendData();
    try {
      for (int i = 0; i < 4000; i++) {
        pipeline.writeInbound(new DatagramPacket(Unpooled.wrappedBuffer(wire),
            new InetSocketAddress("127.0.0.1", 18888),
            new InetSocketAddress("127.0.0.2", 20000 + i)));
      }
      pipeline.checkException();
      Assert.assertTrue(pipeline.isActive());
      Assert.assertEquals(KadService.MAX_PENDING_PONG_TASKS, timer.getQueue().size());
      Assert.assertEquals(4000, pings.get());
      Assert.assertEquals(999, service.getAllNodes().size());
    } finally {
      pipeline.finishAndReleaseAll();
    }
  }

  private void submitDelayedTask() {
    timer.schedule(() -> { }, 60, TimeUnit.SECONDS);
  }
}
