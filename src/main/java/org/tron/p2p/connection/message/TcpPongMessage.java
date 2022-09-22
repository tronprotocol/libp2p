package org.tron.p2p.connection.message;

import org.bouncycastle.pqc.math.linearalgebra.ByteUtils;
import org.tron.p2p.protos.Connect;

public class TcpPongMessage extends Message {

  private Connect.TcpKeepAliveMessage tcpKeepAliveMessage;

  public TcpPongMessage() {
    super(null);
    this.type = Message.PONG;
    this.tcpKeepAliveMessage = Connect.TcpKeepAliveMessage.newBuilder()
        .setTimestamp(System.currentTimeMillis()).build();
    byte[] bytes = new byte[1];
    bytes[0] = Message.PONG;
    this.data = ByteUtils.concatenate(bytes, tcpKeepAliveMessage.toByteArray());
  }

  public long getTimeStamp() {
    return this.tcpKeepAliveMessage.getTimestamp();
  }

}
