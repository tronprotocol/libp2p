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
    //reed from args
    if (args.length == 1) {
      log.info("find active node:{}", args[0]);
      String host = args[0].split(":")[0];
      int port = Integer.parseInt(args[0].split(":")[1]);
      InetSocketAddress activeNode = new InetSocketAddress(host, port);

      List<InetSocketAddress> activeNodes = new ArrayList<>();
      activeNodes.add(activeNode);
      Parameter.p2pConfig.setActiveNodes(activeNodes);

      List<InetAddress> trustNodes = new ArrayList<>();
      trustNodes.add(activeNode.getAddress());
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
}
