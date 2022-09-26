package org.tron.p2p.connection.message.keepalive;

import org.tron.p2p.connection.message.Message;

public class PingMessage extends Message {

  public PingMessage() {
    super(Message.PING, FIXED_PAYLOAD);
  }

}
