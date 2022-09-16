package org.tron.p2p.config;

import java.util.List;
import java.util.Map;
import lombok.Data;
import org.springframework.context.annotation.Configuration;
import org.tron.p2p.P2pConfig;
import org.tron.p2p.P2pEventHandle;

@Data
@Configuration
public class Parameter {

  private int tcpNettyWorkThreadNum = 100;

  private int udpNettyWorkThreadNum = 100;

  private int nodeConnectionTimeout = 2000;

  public static volatile P2pConfig p2pConfig;

  public static volatile List<P2pEventHandle> handleList;

  public static volatile Map<Byte, P2pEventHandle> handleMap;

  public static void addP2pEventHandle(P2pEventHandle p2pEventHandle) {
    handleList.add(p2pEventHandle);
    p2pEventHandle.getTypes().forEach(type -> {
      handleMap.put(type, p2pEventHandle);
    });
  }
}
