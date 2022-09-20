package org.tron.p2p.connection.message;

public class TcpPongMessage extends P2pMessage {

  public TcpPongMessage(byte[] data) {
    super(data);
    this.type = Message.PONG;
  }

  @Override
  public Class<?> getAnswerMessage() {
    return null;
  }

}
