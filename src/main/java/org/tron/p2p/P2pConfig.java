package org.tron.p2p;

import lombok.Data;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@ConfigurationProperties("netconfig")
@Data
@Configuration
public class P2pConfig {
  @Value("${netconfig.tcp_port:1001}")
  private int tcpPort;

  @Value("${netconfig.udp_port:1002}")
  private int udpPort;

  @Value("${netconfig.tcpNettyWorkThreadNum:100}")
  private int tcpNettyWorkThreadNum;

  @Value("${netconfig.udpNettyWorkThreadNum:100}")
  private int udpNettyWorkThreadNum;

  @Value("${netconfig.connection.timeout:2000}")
  private int nodeConnectionTimeout;

}
