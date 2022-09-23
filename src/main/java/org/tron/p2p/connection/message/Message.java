package org.tron.p2p.connection.message;

import org.apache.commons.lang3.ArrayUtils;

public abstract class Message {

  public static final byte PING = (byte) 0xff;
  public static final byte PONG = (byte) 0xfe;
  public static final byte HELLO = (byte) 0xfd;

  protected byte[] data;
  protected byte type;

  public Message(byte[] data) {
    this.data = data;
    this.type = data[0];
  }

  public Message(byte type, byte[] data) {
    this.type = type;
    this.data = ArrayUtils.add(data, 0, type);
  }

  public byte[] getData() {
    return this.data;
  }

  public byte getType() {
    return this.type;
  }

}
