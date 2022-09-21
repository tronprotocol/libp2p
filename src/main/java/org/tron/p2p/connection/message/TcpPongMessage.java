package org.tron.p2p.connection.message;

public class TcpPongMessage extends Message {

  public TcpPongMessage() {
    super(Message.PONG, FIXED_PAYLOAD);
  }

}
