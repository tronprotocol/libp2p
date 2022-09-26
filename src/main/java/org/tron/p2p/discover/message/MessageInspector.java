package org.tron.p2p.discover.message;

import java.util.regex.Pattern;
import org.springframework.util.StringUtils;
import org.tron.p2p.base.Constant;
import org.tron.p2p.discover.Node;
import org.tron.p2p.discover.message.kad.FindNodeMessage;
import org.tron.p2p.discover.message.kad.NeighborsMessage;
import org.tron.p2p.discover.message.kad.PingMessage;
import org.tron.p2p.discover.message.kad.PongMessage;
import org.tron.p2p.discover.protocol.kad.table.KademliaOptions;

public class MessageInspector {

  public static final Pattern PATTERN_IP =
      Pattern.compile("^(1\\d{2}|2[0-4]\\d|25[0-5]|[1-9]\\d|[1-9])\\"
          + ".(1\\d{2}|2[0-4]\\d|25[0-5]|[1-9]\\d|\\d)\\"
          + ".(1\\d{2}|2[0-4]\\d|25[0-5]|[1-9]\\d|\\d)\\"
          + ".(1\\d{2}|2[0-4]\\d|25[0-5]|[1-9]\\d|\\d)$");

  private static boolean isFound(String str, Pattern pattern) {
    if (str == null || pattern == null) {
      return false;
    }
    return pattern.matcher(str).find();
  }

  private static boolean validNode(Node node) {
    if (node == null) {
      return false;
    }
    if (!isFound(node.getHost(), PATTERN_IP)
        || node.getId().length != Constant.NODE_ID_LEN) {
      return false;
    }
    return true;
  }

  private static boolean valid(PingMessage message) {
    return validNode(message.getFrom()) && validNode(message.getTo());
  }

  private static boolean valid(PongMessage message) {
    return validNode(message.getFrom());
  }

  private static boolean valid(FindNodeMessage message) {
    return validNode(message.getFrom())
        && message.getTargetId().length == Constant.NODE_ID_LEN;
  }

  private static boolean valid(NeighborsMessage message) {
    if (!validNode(message.getFrom())) {
      return false;
    }
    if (!StringUtils.isEmpty(message.getNodes())) {
      if (message.getNodes().size() > KademliaOptions.BUCKET_SIZE) {
        return false;
      }
      for (Node node : message.getNodes()) {
        if (!validNode(node)) {
          return false;
        }
      }
    }
    return true;
  }

}
