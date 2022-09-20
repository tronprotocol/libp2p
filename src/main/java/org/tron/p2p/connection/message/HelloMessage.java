package org.tron.p2p.connection.message;

import org.tron.p2p.discover.Node;
import org.tron.p2p.protos.Connect;
import org.tron.p2p.protos.Discover;

public class HelloMessage extends Message {

  private Connect.HelloMessage helloMessage;

  public HelloMessage(byte[] data) throws Exception {
    super(data);
    this.helloMessage = Connect.HelloMessage.parseFrom(data);
  }

  public int getVersion() {
    return this.helloMessage.getVersion();
  }

  public int getCode() {
    return this.helloMessage.getCode();
  }

  public long getTimestamp() {
    return this.helloMessage.getTimestamp();
  }

  public Node getFrom() {
    Discover.Endpoint from = this.helloMessage.getFrom();
    return new Node(from.getNodeId().toByteArray(),
            new String(from.getAddress().toByteArray()), from.getPort());
  }
}
