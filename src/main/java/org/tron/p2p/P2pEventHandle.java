package org.tron.p2p;

import lombok.Getter;
import org.tron.p2p.connection.Channel;

import java.util.List;

public abstract class P2pEventHandle {

  @Getter
  private List<Byte> types;

  public void onConnect(Channel channel) {
  }

  public void onDisconnect(Channel channel) {
  }

  public void onMessage(Channel channel, byte[] data) {
  }
}
