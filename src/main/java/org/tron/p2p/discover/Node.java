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

  private int bindPort;

  private int p2pVersion;

  private long updateTime;

  public Node(byte[] id, String host, int port) {
    this.id = id;
    this.host = host;
    this.port = port;
    this.bindPort = port;
    this.updateTime = System.currentTimeMillis();
  }

  public Node(byte[] id, String host, int port, int bindPort) {
    this.id = id;
    this.host = host;
    this.port = port;
    this.bindPort = bindPort;
    this.updateTime = System.currentTimeMillis();
  }

  public String getHexId() {
    return Hex.toHexString(id);
  }

  public InetSocketAddress getInetSocketAddress() {
    return new InetSocketAddress(host, port);
  }
}
