package org.tron.p2p.connection;

import org.junit.Assert;
import org.junit.Test;
import org.tron.p2p.base.Parameter;

public class ChannelWriteBufferTest {

  @Test
  public void writeBufferHighWaterMarkIsTwiceMaxMessageLength() {
    Assert.assertEquals(2 * Parameter.MAX_MESSAGE_LENGTH, Parameter.WRITE_BUFFER_HIGH_WATER_MARK);
    Assert.assertEquals(10 * 1024 * 1024, Parameter.WRITE_BUFFER_HIGH_WATER_MARK);
  }

  @Test
  public void isWritableFalseBeforeContextSet() {
    // A channel whose ChannelHandlerContext is not wired yet must report not-writable, so a
    // caller never pushes data onto a channel that cannot send it.
    Assert.assertFalse(new Channel().isWritable());
  }
}
