package org.tron.p2p.discover.socket;

public interface EventHandler {

  void channelActivated();

  void handleEvent(UdpEvent event);

}
