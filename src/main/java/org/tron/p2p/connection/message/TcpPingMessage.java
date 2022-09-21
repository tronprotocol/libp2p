package org.tron.p2p.connection.message;

public class TcpPingMessage extends Message {

  public TcpPingMessage() {
    super(Message.PING, FIXED_PAYLOAD);
  }

}
