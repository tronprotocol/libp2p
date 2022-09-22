package org.tron.p2p.connection.message;

import org.bouncycastle.pqc.math.linearalgebra.ByteUtils;
import org.tron.p2p.protos.Connect;

public class TcpPingMessage extends Message {

  private Connect.TcpKeepAliveMessage tcpKeepAliveMessage;

  public TcpPingMessage() {
    super(null);
    this.type = Message.PING;
    this.tcpKeepAliveMessage = Connect.TcpKeepAliveMessage.newBuilder()
        .setTimestamp(System.currentTimeMillis()).build();
    byte[] bytes = new byte[1];
    bytes[0] = Message.PING;
    this.data = ByteUtils.concatenate(bytes, tcpKeepAliveMessage.toByteArray());
  }

  public long getTimeStamp() {
    return this.tcpKeepAliveMessage.getTimestamp();
  }
}
