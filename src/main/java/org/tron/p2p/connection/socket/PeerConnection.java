package org.tron.p2p.connection.socket;


import org.tron.p2p.connection.Channel;

public abstract class PeerConnection extends Channel {

  public abstract void onConnect();

  public abstract void onDisconnect();
}
