package org.tron.p2p.discover;

import java.net.InetSocketAddress;
import java.util.Random;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.bouncycastle.util.encoders.Hex;
import org.tron.p2p.config.Constant;

@Data
@Slf4j(topic = "discover")
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

  public static Node instanceOf(String host, int port) {
    return new Node(Node.getNodeId(), host, port);
  }

  public static byte[] getNodeId() {
    Random gen = new Random();
    byte[] id = new byte[Constant.NODE_ID_LEN];
    gen.nextBytes(id);
    return id;
  }

  public String getHexId() {
    return Hex.toHexString(id);
  }

  public InetSocketAddress getInetSocketAddress() {
    return new InetSocketAddress(host, port);
  }
}
