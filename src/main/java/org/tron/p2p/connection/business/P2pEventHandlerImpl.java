package org.tron.p2p.connection.business;


import org.tron.p2p.P2pEventHandler;
import org.tron.p2p.connection.Channel;

public class P2pEventHandlerImpl extends P2pEventHandler {

  @Override
  public void onMessage(Channel c, byte[] data) {
    switch (data[0]) {
      /*
      case PingMessage: {
        1.send PongMessage
        break;
      }
      case PongMessage: {
        1.handle PongMessage
        break;
      }
       */
    }
  }
}
