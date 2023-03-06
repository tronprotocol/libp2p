package org.tron.p2p.utils;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.net.URL;
import java.net.URLConnection;
import java.util.Enumeration;
import java.util.Random;
import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.tron.p2p.base.Constant;
import org.tron.p2p.discover.Node;
import org.tron.p2p.protos.Discover;

@Slf4j(topic = "net")
public class NetUtil {

  public static final Pattern PATTERN_IPv4 =
      Pattern.compile("^(1\\d{2}|2[0-4]\\d|25[0-5]|[1-9]\\d|[1-9])\\"
          + ".(1\\d{2}|2[0-4]\\d|25[0-5]|[1-9]\\d|\\d)\\"
          + ".(1\\d{2}|2[0-4]\\d|25[0-5]|[1-9]\\d|\\d)\\"
          + ".(1\\d{2}|2[0-4]\\d|25[0-5]|[1-9]\\d|\\d)$");

  //https://codeantenna.com/a/jvrULhCbdj
  public static final Pattern PATTERN_IPv6 = Pattern.compile(
      "^\\s*((([0-9A-Fa-f]{1,4}:){7}([0-9A-Fa-f]{1,4}|:))|(([0-9A-Fa-f]{1,4}:){6}(:[0-9A-Fa-f]{1,4}|((25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)(\\.(25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)){3})|:))|(([0-9A-Fa-f]{1,4}:){5}(((:[0-9A-Fa-f]{1,4}){1,2})|:((25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)(\\.(25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)){3})|:))|(([0-9A-Fa-f]{1,4}:){4}(((:[0-9A-Fa-f]{1,4}){1,3})|((:[0-9A-Fa-f]{1,4})?:((25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)(\\.(25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)){3}))|:))|(([0-9A-Fa-f]{1,4}:){3}(((:[0-9A-Fa-f]{1,4}){1,4})|((:[0-9A-Fa-f]{1,4}){0,2}:((25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)(\\.(25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)){3}))|:))|(([0-9A-Fa-f]{1,4}:){2}(((:[0-9A-Fa-f]{1,4}){1,5})|((:[0-9A-Fa-f]{1,4}){0,3}:((25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)(\\.(25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)){3}))|:))|(([0-9A-Fa-f]{1,4}:){1}(((:[0-9A-Fa-f]{1,4}){1,6})|((:[0-9A-Fa-f]{1,4}){0,4}:((25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)(\\.(25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)){3}))|:))|(:(((:[0-9A-Fa-f]{1,4}){1,7})|((:[0-9A-Fa-f]{1,4}){0,5}:((25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)(\\.(25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)){3}))|:)))(%.+)?\\s*$");

  public static boolean validIpV4(String ip) {
    if (StringUtils.isEmpty(ip)) {
      return false;
    }
    return PATTERN_IPv4.matcher(ip).find();
  }

  public static boolean validIpV6(String ip) {
    if (StringUtils.isEmpty(ip)) {
      return false;
    }
    return PATTERN_IPv6.matcher(ip).find();
  }

  public static boolean validNode(Node node) {
    if (node == null || node.getId() == null) {
      return false;
    }
    if (node.getId().length != Constant.NODE_ID_LEN) {
      return false;
    }
    if (StringUtils.isEmpty(node.getHostV4()) && StringUtils.isEmpty(node.getHostV6())) {
      return false;
    }
    if (StringUtils.isNotEmpty(node.getHostV4()) && !validIpV4(node.getHostV4())) {
      return false;
    }
    if (StringUtils.isNotEmpty(node.getHostV6()) && !validIpV6(node.getHostV6())) {
      return false;
    }
    return true;
  }

  public static Node getNode(Discover.Endpoint endpoint) {
    return new Node(endpoint.getNodeId().toByteArray(),
        ByteArray.toStr(endpoint.getAddress().toByteArray()),
        ByteArray.toStr(endpoint.getAddressIpv6().toByteArray()), endpoint.getPort());
  }

  public static byte[] getNodeId() {
    Random gen = new Random();
    byte[] id = new byte[Constant.NODE_ID_LEN];
    gen.nextBytes(id);
    return id;
  }

  private static String getExternalIp(String url) {
    BufferedReader in = null;
    String ip = null;
    try {
      URLConnection urlConnection = new URL(url).openConnection();
      in = new BufferedReader(new InputStreamReader(urlConnection.getInputStream()));
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
      log.warn("Fail to get {} by {}, cause:{}",
          Constant.AMAZONAWS_URL.equals(url) ? "ipv4" : "ipv6", url, e.getMessage());
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

  private static String getOuterIPv6Address() {
    Enumeration<NetworkInterface> networkInterfaces;
    try {
      networkInterfaces = NetworkInterface.getNetworkInterfaces();
    } catch (SocketException e) {
      log.warn("GetOuterIPv6Address failed, {}", e);
      return null;
    }
    while (networkInterfaces.hasMoreElements()) {
      Enumeration<InetAddress> inetAds = networkInterfaces.nextElement().getInetAddresses();
      while (inetAds.hasMoreElements()) {
        InetAddress inetAddress = inetAds.nextElement();
        if (inetAddress instanceof Inet6Address && !isReservedAddress(inetAddress)) {
          String ipAddress = inetAddress.getHostAddress();
          int index = ipAddress.indexOf('%');
          if (index > 0) {
            ipAddress = ipAddress.substring(0, index);
          }
          return ipAddress;
        }
      }
    }
    return null;
  }

  private static boolean isReservedAddress(InetAddress inetAddress) {
    return inetAddress.isAnyLocalAddress() || inetAddress.isLinkLocalAddress()
        || inetAddress.isLoopbackAddress() || inetAddress.isMulticastAddress();
  }

  public static String getExternalIpV4() {
    return getExternalIp(Constant.AMAZONAWS_URL);
  }

  public static String getExternalIpV6() {
    String ipV6 = getExternalIp(Constant.IDENT_URL);
    if (null == ipV6) {
      ipV6 = getOuterIPv6Address();
    }
    return ipV6;
  }

}
