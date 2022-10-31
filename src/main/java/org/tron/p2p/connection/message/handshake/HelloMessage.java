package org.tron.p2p.connection.message.handshake;

import com.google.protobuf.ByteString;
import java.util.Objects;
import org.apache.commons.lang3.StringUtils;
import org.tron.p2p.base.Parameter;
import org.tron.p2p.connection.business.handshake.DisconnectCode;
import org.tron.p2p.connection.message.Message;
import org.tron.p2p.connection.message.MessageType;
import org.tron.p2p.discover.Node;
import org.tron.p2p.protos.Connect;
import org.tron.p2p.protos.Discover;
import org.tron.p2p.utils.ByteArray;
import org.tron.p2p.utils.NetUtil;

public class HelloMessage extends Message {

  private Connect.HelloMessage helloMessage;

  public HelloMessage(byte[] data) throws Exception {
    super(MessageType.HANDSHAKE_HELLO, data);
    this.helloMessage = Connect.HelloMessage.parseFrom(data);
  }

  public HelloMessage(DisconnectCode code) {
    super(MessageType.HANDSHAKE_HELLO, null);
    Discover.Endpoint.Builder builder = Discover.Endpoint.newBuilder()
        .setNodeId(ByteString.copyFrom(Parameter.p2pConfig.getNodeID()))
        .setPort(Parameter.p2pConfig.getPort());
    if (StringUtils.isNotEmpty(Parameter.p2pConfig.getIp())) {
      builder.setAddress(ByteString.copyFrom(
          Objects.requireNonNull(ByteArray.fromString(Parameter.p2pConfig.getIp()))));
    }
    if (StringUtils.isNotEmpty(Parameter.p2pConfig.getIpv6())) {
      builder.setAddressIpv6(ByteString.copyFrom(
          Objects.requireNonNull(ByteArray.fromString(Parameter.p2pConfig.getIpv6()))));
    }
    Discover.Endpoint endpoint = builder.build();
    this.helloMessage = Connect.HelloMessage.newBuilder()
        .setFrom(endpoint)
        .setVersion(Parameter.p2pConfig.getVersion())
        .setCode(code.getValue())
        .setTimestamp(System.currentTimeMillis()).build();
    this.data = helloMessage.toByteArray();
  }

  public int getVersion() {
    return this.helloMessage.getVersion();
  }

  public int getCode() {
    return this.helloMessage.getCode();
  }

  public long getTimestamp() {
    return this.helloMessage.getTimestamp();
  }

  public Node getFrom() {
    Discover.Endpoint from = this.helloMessage.getFrom();
    return new Node(from.getNodeId().toByteArray(),
        new String(from.getAddress().toByteArray()),
        new String(from.getAddressIpv6().toByteArray()), from.getPort());
  }

  @Override
  public String toString() {
    return "[HelloMessage: " + helloMessage;
  }

  @Override
  public boolean valid() {
    return NetUtil.validNode(getFrom());
  }

}
