package org.tron.p2p.connection.message;

public class Message {

  public static final byte PING = (byte)0xff;
  public static final byte PONG = (byte)0xfe;
  public static final byte HI = (byte)0xfd;

  protected byte[] data;
  protected byte type;

  public Message(byte[] data) {
    this.data = data;
    this.type = data[0];
  }

  public byte[] getData() {
    return this.data;
  }

  public byte getType() {
    return this.type;
  }

}
