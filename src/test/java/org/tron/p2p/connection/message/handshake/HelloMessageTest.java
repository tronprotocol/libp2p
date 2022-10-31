package org.tron.p2p.connection.message.handshake;

import org.bouncycastle.pqc.math.linearalgebra.ByteUtils;
import org.junit.Assert;
import org.junit.Test;
import org.tron.p2p.P2pConfig;
import org.tron.p2p.connection.business.handshake.DisconnectCode;
import org.tron.p2p.connection.message.MessageType;

import static org.tron.p2p.base.Parameter.p2pConfig;

public class HelloMessageTest {

  @Test
  public void testHelloMessage() throws Exception {
    p2pConfig = new P2pConfig();
    HelloMessage m1 = new HelloMessage(DisconnectCode.NORMAL);
    Assert.assertEquals(0, m1.getCode());
    Assert.assertTrue(ByteUtils.equals(p2pConfig.getNodeID(), m1.getFrom().getId()));
    Assert.assertEquals(p2pConfig.getPort(), m1.getFrom().getPort());
    Assert.assertEquals(p2pConfig.getIp(), m1.getFrom().getHostV4());
    Assert.assertEquals(p2pConfig.getVersion(), m1.getVersion());
    Assert.assertEquals(MessageType.HANDSHAKE_HELLO, m1.getType());

    HelloMessage m2 = new HelloMessage(m1.getData());
    Assert.assertTrue(ByteUtils.equals(p2pConfig.getNodeID(), m2.getFrom().getId()));
    Assert.assertEquals(p2pConfig.getPort(), m2.getFrom().getPort());
    Assert.assertEquals(p2pConfig.getIp(), m2.getFrom().getHostV4());
    Assert.assertEquals(p2pConfig.getVersion(), m2.getVersion());
    Assert.assertEquals(MessageType.HANDSHAKE_HELLO, m2.getType());
  }
}
