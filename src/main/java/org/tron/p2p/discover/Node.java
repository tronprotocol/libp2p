package org.tron.p2p.discover;

import java.net.InetSocketAddress;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;
import org.bouncycastle.util.encoders.Hex;

@Data
public class Node {

  private byte[] id;

  private String host;

  private int port;

  @Getter
  private int bindPort;

  @Setter
  private int p2pVersion;

  private boolean isFakeNodeId = false;

  public String getHexId() {
    return Hex.toHexString(id);
  }

  public InetSocketAddress getInetSocketAddress() {
    return new InetSocketAddress(host, port);
  }
}
