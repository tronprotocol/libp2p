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
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
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
import org.tron.p2p.discover.message.kad.PongMessage;
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

  @Test
  public void acceptedPongImmediatelyFreesQueueCapacity() {
    Node node = new Node(new byte[64], "127.0.0.2", "", 18888);
    NodeHandler handler = service.getNodeHandler(node);
    Future<?> timeout = (Future<?>) timer.getQueue().peek();
    for (int i = 1; i < KadService.MAX_PENDING_PONG_TASKS; i++) {
      submitDelayedTask();
    }
    Assert.assertThrows(RejectedExecutionException.class, this::submitDelayedTask);

    handler.handlePong(new PongMessage(node));

    Assert.assertEquals(NodeHandler.State.ACTIVE, handler.getState());
    Assert.assertTrue(timeout.isCancelled());
    Assert.assertEquals(KadService.MAX_PENDING_PONG_TASKS - 1, timer.getQueue().size());
    submitDelayedTask();
    Assert.assertEquals(KadService.MAX_PENDING_PONG_TASKS, timer.getQueue().size());
  }

  @Test
  public void repeatedPingReplacesPreviousTimeout() {
    Node node = new Node(new byte[64], "127.0.0.2", "", 18888);
    NodeHandler handler = service.getNodeHandler(node);
    Future<?> firstTimeout = (Future<?>) timer.getQueue().peek();

    handler.sendPing();

    Assert.assertTrue(firstTimeout.isCancelled());
    Assert.assertEquals(1, timer.getQueue().size());
    handler.handlePong(new PongMessage(node));
    Assert.assertTrue(timer.getQueue().isEmpty());
  }

  @Test
  public void pongDuringSendDoesNotLeaveTimeoutQueued() {
    Node node = new Node(new byte[64], "127.0.0.2", "", 18888);
    NodeHandler handler = service.getNodeHandler(node);
    config.setDiscoverEnable(true);
    service.setMessageSender(event -> {
      if (event.getMessage().getType() == MessageType.KAD_PING) {
        handler.handlePong(new PongMessage(node));
      }
    });

    handler.sendPing();

    Assert.assertEquals(NodeHandler.State.ACTIVE, handler.getState());
    Assert.assertTrue(timer.getQueue().isEmpty());
  }

  @Test
  public void missingPongStillRetriesAndReachesDead() {
    AtomicInteger pings = new AtomicInteger();
    config.setDiscoverEnable(true);
    service.setMessageSender(event -> {
      if (event.getMessage().getType() == MessageType.KAD_PING) {
        pings.incrementAndGet();
      }
    });
    NodeHandler handler = service.getNodeHandler(
        new Node(new byte[64], "127.0.0.2", "", 18888));
    // Execute each queued timeout explicitly instead of waiting for the configured delay.
    for (int i = 0; i < 4; i++) {
      Assert.assertEquals(NodeHandler.State.DISCOVERED, handler.getState());
      Assert.assertEquals(1, timer.getQueue().size());
      Runnable timeout = timer.getQueue().peek();
      Assert.assertTrue(timer.remove(timeout));
      timeout.run();
    }
    Assert.assertEquals(4, pings.get());
    Assert.assertEquals(NodeHandler.State.DEAD, handler.getState());
    Assert.assertTrue(timer.getQueue().isEmpty());
  }

  @Test
  public void cancelledCallbackCannotRetryANewerPing() {
    List<Runnable> callbacks = new ArrayList<>();
    ScheduledThreadPoolExecutor recordingTimer = new ScheduledThreadPoolExecutor(1) {
      @Override
      public ScheduledFuture<?> schedule(Runnable command, long delay, TimeUnit unit) {
        callbacks.add(command);
        return super.schedule(command, delay, unit);
      }
    };
    recordingTimer.setRemoveOnCancelPolicy(true);
    KadService recordingService = new KadService() {
      @Override
      public ScheduledExecutorService getPongTimer() {
        return recordingTimer;
      }
    };
    recordingService.init();
    try {
      Node node = new Node(new byte[64], "127.0.0.2", "", 18888);
      NodeHandler handler = recordingService.getNodeHandler(node);
      Runnable oldCallback = callbacks.get(0);
      handler.handlePong(new PongMessage(node));
      handler.sendPing();

      // Model a callback that started before cancellation and resumes after a newer Ping.
      oldCallback.run();

      Assert.assertEquals(2, callbacks.size());
      Assert.assertEquals(1, recordingTimer.getQueue().size());
      handler.handlePong(new PongMessage(node));
      callbacks.get(1).run();
      Assert.assertEquals(2, callbacks.size());
      Assert.assertTrue(recordingTimer.getQueue().isEmpty());
    } finally {
      recordingService.close();
      recordingTimer.shutdownNow();
    }
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
    int threadCount = 8;
    int tasksPerThread = KadService.MAX_PENDING_PONG_TASKS / threadCount + 100;
    ExecutorService executor = Executors.newFixedThreadPool(threadCount);
    CountDownLatch start = new CountDownLatch(1);
    List<Future<Integer>> results = new ArrayList<>();
    try {
      for (int i = 0; i < threadCount; i++) {
        results.add(executor.submit(() -> {
          Assert.assertTrue(start.await(5, TimeUnit.SECONDS));
          int accepted = 0;
          for (int j = 0; j < tasksPerThread; j++) {
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
      // Leave room for some datagrams, then exercise overflow without scaling packet processing.
      int availableSlots = Math.min(2000, KadService.MAX_PENDING_PONG_TASKS);
      for (int i = availableSlots; i < KadService.MAX_PENDING_PONG_TASKS; i++) {
        submitDelayedTask();
      }
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
