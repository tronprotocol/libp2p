package org.tron.p2p.connection.message.base;

import org.tron.p2p.connection.message.Message;
import org.tron.p2p.connection.message.MessageType;
import org.tron.p2p.protos.Connect;
import org.tron.p2p.protos.Connect.DisconnectReason;


public class P2pDisconnectMessage extends Message {

  private Connect.P2pDisconnectMessage p2pDisconnectMessage;

  public P2pDisconnectMessage(DisconnectReason disconnectReason) {
    super(MessageType.DISCONNECT, null);
    this.p2pDisconnectMessage = Connect.P2pDisconnectMessage.newBuilder()
        .setReason(disconnectReason).build();
    this.data = p2pDisconnectMessage.toByteArray();
  }

  @Override
  public boolean valid() {
    return true;
  }
}
