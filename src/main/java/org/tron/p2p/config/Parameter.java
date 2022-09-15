package org.tron.p2p.config;

import org.tron.p2p.P2pConfig;
import org.tron.p2p.P2pEventHandle;

import java.util.List;
import java.util.Map;

public class Parameter {

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
