package org.tron.p2p.connection.business.handshake;

public enum DisconnectCode {
  NORMAL(0),
  TOO_MANY_PEERS(1),
  DIFFERENT_VERSION(2),
  TIME_BANNED(3),
  DUPLICATE_PEER(4),
  MAX_CONNECTION_WITH_SAME_IP(5),
  PONG_TIME_OUT(6),
  RANDOM_DISCONNECT(7),
  UNKNOWN(255);

  private final Integer value;

  DisconnectCode(Integer value) {
    this.value = value;
  }

  public Integer getValue() {
    return value;
  }

}
