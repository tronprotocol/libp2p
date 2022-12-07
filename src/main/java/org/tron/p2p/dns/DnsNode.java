package org.tron.p2p.dns;


import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.tron.p2p.discover.Node;
import org.tron.p2p.dns.tree.Algorithm;

@Slf4j(topic = "net")
public class DnsNode extends Node implements Comparable<DnsNode> {

  private int v4Int;

  public DnsNode(byte[] id, String hostV4, String hostV6, int port) {
    super(id, hostV4, hostV6, port);
    this.v4Int = v4ToInt();
  }

  public DnsNode(JSONObject jsonObject) {
    super(null, jsonObject.containsKey("v4") ? intToV4(jsonObject.getIntValue("v4")) : null,
        jsonObject.getString("v6"), jsonObject.getIntValue("port"));
    this.v4Int = v4ToInt();
  }

  public JSONObject toJson() {
    JSONObject jsonObject = new JSONObject();
    if (StringUtils.isNotEmpty(hostV4)) {
      jsonObject.put("v4", v4ToInt());
    }
    if (StringUtils.isNotEmpty(hostV6)) {
      jsonObject.put("v6", hostV6);
    }
    jsonObject.put("port", port);
    return jsonObject;
  }

  public static String compress(List<DnsNode> nodes) {
    JSONArray jsonArray = new JSONArray();
    for (DnsNode dnsNode : nodes) {
      jsonArray.add(dnsNode.toJson());
    }
    return Algorithm.encode64(jsonArray.toString().getBytes());
  }

  public static List<DnsNode> decompress(String base64Content) {
    byte[] data;
    try {
      data = Algorithm.decode64(base64Content);
    } catch (Exception e) {
      log.error("", e);
      return null;
    }
    String content = new String(data);
    JSONArray jsonArray = JSONArray.parseArray(content);
    List<DnsNode> dnsNodes = new ArrayList<>();
    for (Object o : jsonArray) {
      JSONObject jsonObject = (JSONObject) o;
      DnsNode dnsNode = new DnsNode(jsonObject);
      dnsNodes.add(dnsNode);
    }
    return dnsNodes;
  }

  private int v4ToInt() {
    byte[] bytes;
    try {
      bytes = InetAddress.getByName(hostV4).getAddress();
    } catch (UnknownHostException e) {
      return 0;
    }
    int addr = bytes[3] & 0xFF;
    addr |= ((bytes[2] << 8) & 0xFF00);
    addr |= ((bytes[1] << 16) & 0xFF0000);
    addr |= ((bytes[0] << 24) & 0xFF000000);
    return addr;
  }

  private static String intToV4(int ipInt) {
    String[] ipString = new String[4];
    for (int i = 0; i < 4; i++) {
      int pos = i * 8;
      int and = ipInt & (255 << pos);
      ipString[i] = String.valueOf(and >>> pos);
    }
    return String.join(".", ipString);
  }

  @Override
  public int compareTo(DnsNode o) {
    if (this.v4Int > o.v4Int) {
      return 1;
    } else if (this.v4Int < o.v4Int) {
      return -1;
    } else {
      return this.port - o.port;
    }
  }
}
