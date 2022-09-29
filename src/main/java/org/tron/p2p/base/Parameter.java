package org.tron.p2p.base;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.Data;
import org.tron.p2p.P2pConfig;
import org.tron.p2p.P2pEventHandler;
import org.tron.p2p.exception.P2pException;

@Data
public class Parameter {

  public static final int TCP_NETTY_WORK_THREAD_NUM = 100;

  public static final int UDP_NETTY_WORK_THREAD_NUM = 1;

  public static final int NODE_CONNECTION_TIMEOUT = 2000;

  public static final int KEEP_ALIVE_PERIOD = 20_000;

  public static final int NETWORK_TIME_DIFF = 1000;

  public static final long DEFAULT_BAN_TIME = 60_000;

  public static volatile P2pConfig p2pConfig;

  public static volatile List<P2pEventHandler> handlerList = new ArrayList<>();

  public static volatile Map<Byte, P2pEventHandler> handlerMap = new HashMap<>();

  public static boolean addP2pEventHandle(P2pEventHandler p2PEventHandler) {
    handlerList.add(p2PEventHandler);
    if (p2PEventHandler.getTypes() == null) {
      return true;
    }
    for (Byte type: p2PEventHandler.getTypes()) {
      if (handlerMap.get(type) != null) {
        return false;
      }
    }
    for (Byte type: p2PEventHandler.getTypes()) {
      handlerMap.put(type, p2PEventHandler);
    }
    return true;
  }
}
