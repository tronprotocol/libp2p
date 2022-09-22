package org.tron.p2p.config;

import java.util.List;
import java.util.Map;
import lombok.Data;
import org.springframework.context.annotation.Configuration;
import org.tron.p2p.P2pConfig;
import org.tron.p2p.P2pEventHandler;

@Data
@Configuration
public class Parameter {

  public static int tcpNettyWorkThreadNum = 100;

  public static int udpNettyWorkThreadNum = 1;

  public static int nodeConnectionTimeout = 2000;

  public static volatile P2pConfig p2pConfig;

  public static volatile List<P2pEventHandler> handlerList;

  public static volatile Map<Byte, P2pEventHandler> handlerMap;

  public static void addP2pEventHandle(P2pEventHandler p2PEventHandler) {
    handlerList.add(p2PEventHandler);
    p2PEventHandler.getTypes().forEach(type -> {
      handlerMap.put(type, p2PEventHandler);
    });
  }
}
