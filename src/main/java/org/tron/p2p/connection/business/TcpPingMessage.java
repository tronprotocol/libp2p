package org.tron.p2p.connection.business;

import org.bouncycastle.util.encoders.Hex;
import org.tron.p2p.connection.tcp.MessageTypes;
import org.tron.p2p.connection.tcp.P2pMessage;


public class TcpPingMessage extends P2pMessage {

  private static final byte[] FIXED_PAYLOAD = Hex.decode("C0");

  public TcpPingMessage() {
    super(MessageTypes.P2P_PING.asByte(), FIXED_PAYLOAD);
  }

  public TcpPingMessage(byte type, byte[] rawData) {
    super(type, rawData);
  }

  @Override
  public byte[] getData() {
    return FIXED_PAYLOAD;
  }


  @Override
  public Class<?> getAnswerMessage() {
    return TcpPongMessage.class;
  }

}
