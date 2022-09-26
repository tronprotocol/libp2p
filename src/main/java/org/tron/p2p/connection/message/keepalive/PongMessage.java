package org.tron.p2p.connection.message.keepalive;

import org.bouncycastle.pqc.math.linearalgebra.ByteUtils;
import org.tron.p2p.connection.message.Message;
import org.tron.p2p.connection.message.MessageType;
import org.tron.p2p.protos.Connect;

public class PongMessage extends Message {

  private Connect.TcpKeepAliveMessage tcpKeepAliveMessage;

  public PongMessage(byte[] data) throws Exception {
    super(MessageType.KEEP_ALIVE_PONG, data);
    this.tcpKeepAliveMessage = Connect.TcpKeepAliveMessage.parseFrom(data);
  }

  public PongMessage() {
    super(null, null);
    this.type = MessageType.KEEP_ALIVE_PONG;
    tcpKeepAliveMessage = Connect.TcpKeepAliveMessage.newBuilder()
        .setTimestamp(System.currentTimeMillis()).build();
    byte[] bytes = new byte[1];
    bytes[0] = MessageType.KEEP_ALIVE_PONG.getType();
    this.data = ByteUtils.concatenate(bytes, tcpKeepAliveMessage.toByteArray());
  }

  public long getTimeStamp() {
    return this.tcpKeepAliveMessage.getTimestamp();
  }

  @Override
  public boolean valid() {
    if (getTimeStamp() <= 0 || getTimeStamp() > System.currentTimeMillis() + 1000) {
      return false;
    }
    return true;
  }
}
