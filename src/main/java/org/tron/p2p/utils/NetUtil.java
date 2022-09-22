package org.tron.p2p.utils;

import org.tron.p2p.config.Constant;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.InetAddress;
import java.net.URL;

public class NetUtil {

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
      return ip;
    } finally {
      if (in != null) {
        try {
          in.close();
        } catch (IOException e) {}
      }
    }
  }
}
