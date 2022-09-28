package org.tron.p2p.utils;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.InetAddress;
import java.net.URL;
import java.util.Random;
import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;
import org.tron.p2p.base.Constant;
import org.tron.p2p.discover.Node;
import org.tron.p2p.protos.Discover;

@Slf4j(topic = "net")
public class NetUtil {

  public static final Pattern PATTERN_IP =
      Pattern.compile("^(1\\d{2}|2[0-4]\\d|25[0-5]|[1-9]\\d|[1-9])\\"
          + ".(1\\d{2}|2[0-4]\\d|25[0-5]|[1-9]\\d|\\d)\\"
          + ".(1\\d{2}|2[0-4]\\d|25[0-5]|[1-9]\\d|\\d)\\"
          + ".(1\\d{2}|2[0-4]\\d|25[0-5]|[1-9]\\d|\\d)$");

  public static boolean validIp(String ip) {
    if (ip == null) {
      return false;
    }
    return PATTERN_IP.matcher(ip).find();
  }

  public static boolean validNode(Node node) {
    if (node == null || node.getId() == null) {
      return false;
    }
    return validIp(node.getHost()) && node.getId().length == Constant.NODE_ID_LEN;
  }

  public static Node getNode(Discover.Endpoint endpoint) {
    return new Node(endpoint.getNodeId().toByteArray(),
        ByteArray.toStr(endpoint.getAddress().toByteArray()), endpoint.getPort());
  }

  public static byte[] getNodeId() {
    Random gen = new Random();
    byte[] id = new byte[Constant.NODE_ID_LEN];
    gen.nextBytes(id);
    return id;
  }

  public static String getExternalIp() {
    BufferedReader in = null;
    String ip = null;
    try {
      in = new BufferedReader(new InputStreamReader(
          new URL(Constant.AMAZONAWS_URL).openStream()));
      ip = in.readLine();
      if (ip == null || ip.trim().isEmpty()) {
        throw new IOException("Invalid address: " + ip);
      }
      try {
        InetAddress.getByName(ip);
      } catch (Exception e) {
        throw new IOException("Invalid address: " + ip);
      }
      return ip;
    } catch (IOException e) {
      log.warn("Fail to get ip, {}", e.getMessage());
      return ip;
    } finally {
      if (in != null) {
        try {
          in.close();
        } catch (IOException e) {
        }
      }
    }
  }
}
