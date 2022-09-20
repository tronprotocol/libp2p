package org.tron.p2p.connection.message;

public class TcpPingMessage extends P2pMessage {

  public TcpPingMessage(byte[] data) {
    super(data);
    this.type = Message.PING;
  }

  @Override
  public Class<?> getAnswerMessage() {
    return TcpPongMessage.class;
  }
}
