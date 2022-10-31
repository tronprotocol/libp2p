package org.tron.p2p.discover.message.kad;

import com.google.protobuf.ByteString;
import java.util.Objects;
import org.apache.commons.lang3.StringUtils;
import org.tron.p2p.discover.Node;
import org.tron.p2p.discover.message.Message;
import org.tron.p2p.discover.message.MessageType;
import org.tron.p2p.protos.Discover.Endpoint;
import org.tron.p2p.utils.ByteArray;

public abstract class KadMessage extends Message {

  protected KadMessage(MessageType type, byte[] data) {
    super(type, data);
  }

  public abstract Node getFrom();

  public abstract long getTimestamp();

  public Endpoint getEndpointFromNode(Node node) {
    Endpoint.Builder builder = Endpoint.newBuilder()
        .setPort(node.getPort())
        .setNodeId(ByteString.copyFrom(node.getId()));
    if (StringUtils.isNotEmpty(node.getHostV4())) {
      builder.setAddress(ByteString.copyFrom(
          Objects.requireNonNull(ByteArray.fromString(node.getHostV4()))));
    }
    if (StringUtils.isNotEmpty(node.getHostV6())) {
      builder.setAddressIpv6(ByteString.copyFrom(
          Objects.requireNonNull(ByteArray.fromString(node.getHostV6()))));
    }
    return builder.build();
  }
}
