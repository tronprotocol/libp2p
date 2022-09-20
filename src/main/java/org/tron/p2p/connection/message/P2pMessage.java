package org.tron.p2p.connection.message;


public abstract class P2pMessage extends Message {


  public P2pMessage(byte[] data) {
    super(data);
  }

  public abstract Class<?> getAnswerMessage();

}
