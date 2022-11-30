package org.tron.p2p.dns;


import com.alibaba.fastjson2.JSONObject;
import java.util.List;
import org.apache.commons.lang3.StringUtils;
import org.tron.p2p.discover.Node;

public class DnsNode extends Node {

  public DnsNode(JSONObject jsonObject) {
    super(null, jsonObject.getString("v4"), jsonObject.getString("v6"),
        jsonObject.getIntValue("port"));
  }

  public JSONObject toJson() {
    JSONObject jsonObject = new JSONObject();
    if (StringUtils.isNotEmpty(hostV4)) {
      jsonObject.put("v4", hostV4);
    }
    if (StringUtils.isNotEmpty(hostV6)) {
      jsonObject.put("v6", hostV6);
    }
    jsonObject.put("port", port);
    return jsonObject;
  }

  public static String compress(List<DnsNode> nodes) {
    //todo
    return null;
  }

  public static List<DnsNode> decompress(String content) {
    //todo
    return null;
  }
}
