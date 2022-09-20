package org.tron.p2p.discover.socket;

import org.tron.p2p.discover.socket.message.Message;

import java.net.InetSocketAddress;

public class UdpEvent {
  private Message message;
  private InetSocketAddress address;

  public UdpEvent(Message message, InetSocketAddress address) {
    this.message = message;
    this.address = address;
  }

  public Message getMessage() {
    return message;
  }

  public void setMessage(Message message) {
    this.message = message;
  }

  public InetSocketAddress getAddress() {
    return address;
  }

  public void setAddress(InetSocketAddress address) {
    this.address = address;
  }
}
