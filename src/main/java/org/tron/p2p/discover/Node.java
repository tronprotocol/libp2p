package org.tron.p2p.discover;

import java.io.Serializable;
import java.net.Inet4Address;
import java.net.InetSocketAddress;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.bouncycastle.util.encoders.Hex;
import org.tron.p2p.base.Parameter;
import org.tron.p2p.utils.NetUtil;

@Slf4j(topic = "net")
public class Node implements Serializable, Cloneable {

  private static final long serialVersionUID = -4267600517925770636L;

  private byte[] id;

  private String hostV4;

  private String hostV6;

  private int port;

  private int bindPort;

  @Setter
  private int p2pVersion;

  private long updateTime;

  public Node(InetSocketAddress address) {
    this.id = NetUtil.getNodeId();
    if (address.getAddress() instanceof Inet4Address) {
      this.hostV4 = address.getAddress().getHostAddress();
    } else {
      this.hostV6 = address.getAddress().getHostAddress();
    }
    this.port = address.getPort();
    this.bindPort = port;
    this.updateTime = System.currentTimeMillis();
    formatHostV6();
  }

  public Node(byte[] id, String hostV4, String hostV6, int port) {
    this.id = id;
    this.hostV4 = hostV4;
    this.hostV6 = hostV6;
    this.port = port;
    this.bindPort = port;
    this.updateTime = System.currentTimeMillis();
    formatHostV6();
  }

  public Node(byte[] id, String hostV4, String hostV6, int port, int bindPort) {
    this.id = id;
    this.hostV4 = hostV4;
    this.hostV6 = hostV6;
    this.port = port;
    this.bindPort = bindPort;
    this.updateTime = System.currentTimeMillis();
    formatHostV6();
  }

  public void updateHostV4(String hostV4) {
    if (StringUtils.isEmpty(this.hostV4) && StringUtils.isNotEmpty(hostV4)) {
      log.info("update hostV4:{} with hostV6:{}", hostV4, this.hostV6);
      this.hostV4 = hostV4;
    }
  }

  public void updateHostV6(String hostV6) {
    if (StringUtils.isEmpty(this.hostV6) && StringUtils.isNotEmpty(hostV6)) {
      log.info("update hostV6:{} with hostV4:{}", hostV6, this.hostV4);
      this.hostV6 = hostV6;
    }
  }

  //use standard ipv6 format
  private void formatHostV6() {
    if (StringUtils.isNotEmpty(this.hostV6)) {
      this.hostV6 = new InetSocketAddress(hostV6, port).getAddress().getHostAddress();
    }
  }

  public boolean isConnectible(int argsP2PVersion) {
    return port == bindPort && p2pVersion == argsP2PVersion;
  }

  public boolean isIpStackCompatible() {
    return isIpV4Compatible() || isIpV6Compatible();
  }

  public boolean isIpV4Compatible() {
    return supportV4() && StringUtils.isNotEmpty(Parameter.p2pConfig.getIp());
  }

  public boolean isIpV6Compatible() {
    return supportV6() && StringUtils.isNotEmpty(Parameter.p2pConfig.getIpv6());
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

  public String getHostV4() {
    return hostV4;
  }

  public String getHostV6() {
    return hostV6;
  }

  //node that exists in kad table has whole hostV4 and hostV6 if it support v4 and v6
  public String getHostKey() {
    StringBuilder sb = new StringBuilder();
    if (StringUtils.isNotEmpty(hostV4)) {
      sb.append(hostV4);
    }
    sb.append("-");
    if (StringUtils.isNotEmpty(hostV6)) {
      sb.append(hostV6);
    }
    return sb.toString();
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
    return "Node{" + " hostV4='" + hostV4 + '\'' + ", hostV6='" + hostV6 + '\'' + ", port=" + port
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

  public InetSocketAddress getInetSocketV4() {
    return StringUtils.isNotEmpty(hostV4) ? new InetSocketAddress(hostV4, port) : null;
  }

  public InetSocketAddress getInetSocketV6() {
    return StringUtils.isNotEmpty(hostV6) ? new InetSocketAddress(hostV6, port) : null;
  }

  private boolean supportV4() {
    return StringUtils.isNotEmpty(hostV4);
  }

  private boolean supportV6() {
    return StringUtils.isNotEmpty(hostV6);
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
