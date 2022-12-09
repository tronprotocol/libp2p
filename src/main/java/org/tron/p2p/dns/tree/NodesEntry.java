package org.tron.p2p.dns.tree;


import com.google.protobuf.InvalidProtocolBufferException;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.tron.p2p.dns.DnsNode;
import org.tron.p2p.exception.InvalidEnrException;

@Slf4j(topic = "net")
public class NodesEntry implements Entry {

  private String represent;
  @Getter
  private List<DnsNode> nodes;

  public NodesEntry(String represent, List<DnsNode> nodes) {
    this.represent = represent;
    this.nodes = nodes;
  }

  //for eth
  public static NodesEntry parseEntry(String e) {
    return new NodesEntry(e, new ArrayList<>());
  }

  //for tron
  public static NodesEntry parseEntry1(String e) throws InvalidEnrException {
    String content = e.substring(enrPrefix.length());
    List<DnsNode> nodeList;
    try {
      nodeList = DnsNode.decompress(content);
    } catch (InvalidProtocolBufferException | UnknownHostException ex) {
      throw new InvalidEnrException(ex);
    }
    return new NodesEntry(e, nodeList);
  }

  @Override
  public String toString() {
    return represent;
  }
}
