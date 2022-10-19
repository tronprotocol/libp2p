package org.tron.p2p.discover.message.kad;

import org.tron.p2p.discover.Node;
import org.tron.p2p.discover.message.Message;
import org.tron.p2p.discover.message.MessageType;

public abstract class KadMessage extends Message {

  protected KadMessage(MessageType type, byte[] data) {
    super(type, data);
  }

  public abstract Node getFrom();
  public abstract long getTimestamp();
}
