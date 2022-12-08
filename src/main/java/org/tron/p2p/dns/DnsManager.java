package org.tron.p2p.dns;


import java.util.List;
import java.util.function.Consumer;
import org.tron.p2p.discover.DiscoverService;
import org.tron.p2p.discover.Node;
import org.tron.p2p.discover.socket.UdpEvent;

public class DnsManager implements DiscoverService {

  @Override
  public void init() {

  }

  @Override
  public void close() {

  }

  @Override
  public List<Node> getConnectableNodes() {
    throw new RuntimeException("not support");
  }

  @Override
  public List<Node> getTableNodes() {
    throw new RuntimeException("not support");
  }

  @Override
  public List<Node> getAllNodes() {
    return null;
  }

  @Override
  public Node getPublicHomeNode() {
    throw new RuntimeException("not support");
  }

  @Override
  public void channelActivated() {
    throw new RuntimeException("not support");
  }

  @Override
  public void handleEvent(UdpEvent event) {
    throw new RuntimeException("not support");
  }

  @Override
  public void setMessageSender(Consumer<UdpEvent> messageSender) {
    throw new RuntimeException("not support");
  }
}
