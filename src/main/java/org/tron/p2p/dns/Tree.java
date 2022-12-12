package org.tron.p2p.dns;

import lombok.Data;
import org.tron.p2p.protos.Connect;

import java.util.List;

@Data
public class Tree {
  private Tree father;
  private List<Tree> children;
  private Connect.DnsNode value;
}
