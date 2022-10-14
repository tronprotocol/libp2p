package org.tron.p2p.example;


import static java.lang.Thread.sleep;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.tron.p2p.P2pConfig;
import org.tron.p2p.P2pService;
import org.tron.p2p.base.Parameter;

@Slf4j(topic = "net")
public class StartApp {

  public static void main(String[] args) {
    P2pService p2pService = new P2pService();
    Parameter.p2pConfig = new P2pConfig();

    log.info("args size:{}", args.length);

    if (args.length >= 1) {
      log.info("find seed node:{}", args[0]);
      Parameter.p2pConfig.setSeedNodes(parse(args[0]));
    }
    if (args.length >= 2) {
      log.info("find active node:{}", args[1]);
      Parameter.p2pConfig.setActiveNodes(parse(args[1]));
    }
    if (args.length >= 3) {
      log.info("find trust node:{}", args[2]);
      InetSocketAddress address = new InetSocketAddress(args[2], Parameter.p2pConfig.getPort());
      List<InetAddress> trustNodes = new ArrayList<>();
      trustNodes.add(address.getAddress());
      Parameter.p2pConfig.setTrustNodes(trustNodes);
    }

    p2pService.start(Parameter.p2pConfig);

    while (true) {
      try {
        sleep(1000);
      } catch (InterruptedException e) {
        break;
      }
    }
  }

  private static List<InetSocketAddress> parse(String para) {
    String host = para.split(":")[0];
    int port = Integer.parseInt(para.split(":")[1]);
    InetSocketAddress address = new InetSocketAddress(host, port);
    List<InetSocketAddress> nodes = new ArrayList<>();
    nodes.add(address);
    return nodes;
  }
}
