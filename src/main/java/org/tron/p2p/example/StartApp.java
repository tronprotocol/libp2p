package org.tron.p2p.example;


import static java.lang.Thread.sleep;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.List;

import com.google.gson.JsonNull;
import com.google.gson.JsonParser;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.commons.lang3.StringUtils;
import org.tron.p2p.P2pConfig;
import org.tron.p2p.P2pService;
import org.tron.p2p.base.Parameter;

@Slf4j(topic = "net")
public class StartApp {

  public static void main(String[] args) {
    Parameter.version = 1;

    P2pService p2pService = new P2pService();
    Parameter.p2pConfig = new P2pConfig();

    CommandLine cli = null;
    try {
      cli = parseCli(args);
    } catch (ParseException e) {
      System.exit(0);
    }

    if (cli.hasOption("s")) {
      Parameter.p2pConfig.setSeedNodes(parse(cli.getOptionValue("s")));
      log.info("Seed nodes {}", Parameter.p2pConfig.getSeedNodes());
    }

    if (cli.hasOption("a")) {
      Parameter.p2pConfig.setActiveNodes(parse(cli.getOptionValue("a")));
      log.info("Active nodes {}", Parameter.p2pConfig.getActiveNodes());
    }

    if (cli.hasOption("t")) {
      InetSocketAddress address = new InetSocketAddress(cli.getOptionValue("t"), 0);
      List<InetAddress> trustNodes = new ArrayList<>();
      trustNodes.add(address.getAddress());
      Parameter.p2pConfig.setTrustNodes(trustNodes);
      log.info("Trust nodes {}", Parameter.p2pConfig.getTrustNodes());
    }

    if (cli.hasOption("M")) {
      Parameter.p2pConfig.setMaxConnections(Integer.parseInt(cli.getOptionValue("M")));
    }

    if (cli.hasOption("m")) {
      Parameter.p2pConfig.setMinConnections(Integer.parseInt(cli.getOptionValue("m")));
    }

    if (Parameter.p2pConfig.getMinConnections() > Parameter.p2pConfig.getMaxConnections()) {
      log.error("Check maxConnections({}) >= minConnections({}) failed",
        Parameter.p2pConfig.getMaxConnections(), Parameter.p2pConfig.getMinConnections());
      System.exit(0);
    }

    if (cli.hasOption("d")) {
      int d = Integer.parseInt(cli.getOptionValue("d"));
      if (d != 0 && d != 1) {
        log.error("Check discover failed, must be 0/1");
        System.exit(0);
      }
      Parameter.p2pConfig.setDiscoverEnable(d == 1);
    }

    if (cli.hasOption("p")) {
      Parameter.p2pConfig.setPort(Integer.parseInt(cli.getOptionValue("p")));
    }

    if (cli.hasOption("v")) {
      Parameter.p2pConfig.setNetworkId(Integer.parseInt(cli.getOptionValue("v")));
    }
    if (StringUtils.isNotEmpty(Parameter.p2pConfig.getIpv6())) {
      log.info("Local ipv6: {}", Parameter.p2pConfig.getIpv6());
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

  private static CommandLine parseCli(String[] args) throws ParseException {
    Option opt1 = new Option("s", "seed-nodes", true,
      "seed node(s), required, ip:port[,ip:port[...]]");
    opt1.setRequired(false);
    Option opt2 = new Option("t", "trust-ips", true, "trust ip(s), ip[,ip[...]]");
    opt2.setRequired(false);
    Option opt3 = new Option("a", "active-nodes", true, "active node(s), ip:port[,ip:port[...]]");
    opt3.setRequired(false);
    Option opt4 = new Option("M", "max-connection", true, "max connection number, int, default 50");
    opt4.setRequired(false);
    Option opt5 = new Option("m", "min-connection", true, "min connection number, int, default 8");
    opt5.setRequired(false);
    Option opt6 = new Option("d", "discover", true, "enable p2p discover, 0/1, default 1");
    opt6.setRequired(false);
    Option opt7 = new Option("p", "port", true, "UDP & TCP port, int, default 18888");
    opt7.setRequired(false);
    Option opt8 = new Option("v", "version", true, "p2p version, int, default 1");
    opt8.setRequired(false);

    Options options = new Options();
    options.addOption(opt1);
    options.addOption(opt2);
    options.addOption(opt3);
    options.addOption(opt4);
    options.addOption(opt5);
    options.addOption(opt6);
    options.addOption(opt7);
    options.addOption(opt8);

    CommandLine cli;
    CommandLineParser cliParser = new DefaultParser();
    HelpFormatter helpFormatter = new HelpFormatter();

    try {
      cli = cliParser.parse(options, args);
    } catch (ParseException e) {
      helpFormatter.printHelp(">>>>>> available cli options", options);
      throw e;
    }

    return cli;
  }

  private static List<InetSocketAddress> parse(String paras) {
    List<InetSocketAddress> nodes = new ArrayList<>();
    for (String para : paras.split(",")) {
      int index = para.lastIndexOf(":");
      if (index > 0) {
        String host = para.substring(0, index);
        int port = Integer.parseInt(para.substring(index + 1));
        InetSocketAddress address = new InetSocketAddress(host, port);
        nodes.add(address);
      }
    }
    return nodes;
  }
}
