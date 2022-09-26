package org.tron.p2p.connection.message.keepalive;

import org.tron.p2p.connection.message.Message;

public class PongMessage extends Message {

  public PongMessage() {
    super(Message.PONG, FIXED_PAYLOAD);
  }

}
