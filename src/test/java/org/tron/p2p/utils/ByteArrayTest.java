package org.tron.p2p.utils;

import org.junit.Assert;
import org.junit.Test;


public class ByteArrayTest {

  @Test
  public void testHexToString() {
    byte[] data = new byte[] {-128, -127, -1, 0, 1, 127};
    Assert.assertEquals("8081ff00017f", ByteArray.toHexString(data));
  }
}
