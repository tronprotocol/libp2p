package org.tron.p2p.connection.tcp;


public abstract class P2pMessage extends Message {

  public P2pMessage() {
  }

  public P2pMessage(byte[] rawData) {
    super(rawData);
  }

  public P2pMessage(byte type, byte[] rawData) {
    super(type, rawData);
  }

}
