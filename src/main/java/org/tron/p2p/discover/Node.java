package org.tron.p2p.discover;

import java.io.Serializable;
import java.net.InetSocketAddress;
import java.util.Random;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.bouncycastle.util.encoders.Hex;
import org.tron.p2p.base.Constant;
import org.tron.p2p.utils.NetUtil;

@Slf4j(topic = "net")
public class Node implements Serializable, Cloneable {

  private static final long serialVersionUID = -4267600517925770636L;

  private byte[] id;

  private String host;

  private int port;

  private int bindPort;

  @Setter
  private int p2pVersion;

  private long updateTime;

  public Node(InetSocketAddress address) {
    this.id = NetUtil.getNodeId();
    this.host = address.getAddress().getHostAddress();
    this.port = address.getPort();
    this.bindPort = port;
    this.updateTime = System.currentTimeMillis();
  }

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

  public boolean isConnectible(int argsP2PVersion) {
    return port == bindPort && p2pVersion == argsP2PVersion;
  }

  public String getHexId() {
    return Hex.toHexString(id);
  }

  public String getHexIdShort() {
    return getIdShort(getHexId());
  }

  public byte[] getId() {
    return id;
  }

  public void setId(byte[] id) {
    this.id = id;
  }

  public String getHost() {
    return host;
  }

  public int getPort() {
    return port;
  }

  public void setPort(int port) {
    this.port = port;
  }

  public String getIdString() {
    if (id == null) {
      return null;
    }
    return new String(id);
  }

  public long getUpdateTime() {
    return updateTime;
  }

  public void touch() {
    updateTime = System.currentTimeMillis();
  }

  @Override
  public String toString() {
    return "Node{" + " host='" + host + '\'' + ", port=" + port
        + ", id=" + Hex.toHexString(id) + '}';
  }

  @Override
  public int hashCode() {
    return this.toString().hashCode();
  }

  @Override
  public boolean equals(Object o) {
    if (o == null) {
      return false;
    }

    if (o == this) {
      return true;
    }

    if (o.getClass() == getClass()) {
      return StringUtils.equals(getIdString(), ((Node) o).getIdString());
    }

    return false;
  }

  private String getIdShort(String Id) {
    return Id == null ? "<null>" : Id.substring(0, 8);
  }

  public InetSocketAddress getInetSocketAddress() {
    return new InetSocketAddress(host, port);
  }

  @Override
  public Object clone() {
    try {
      return super.clone();
    } catch (CloneNotSupportedException ignored) {
    }
    return null;
  }
}
