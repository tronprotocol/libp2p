package org.tron.p2p.connection.message.handshake;

import com.google.protobuf.ByteString;
import org.tron.p2p.base.Parameter;
import org.tron.p2p.connection.business.handshake.DisconnectCode;
import org.tron.p2p.connection.message.Message;
import org.tron.p2p.connection.message.MessageType;
import org.tron.p2p.discover.Node;
import org.tron.p2p.protos.Connect;
import org.tron.p2p.protos.Discover;
import org.tron.p2p.utils.NetUtil;

public class HelloMessage extends Message {

  private Connect.HelloMessage helloMessage;

  public HelloMessage(byte[] data) throws Exception {
    super(MessageType.HANDSHAKE_HELLO, data);
    this.helloMessage = Connect.HelloMessage.parseFrom(data);
  }

  public HelloMessage(DisconnectCode code) {
    super(MessageType.HANDSHAKE_HELLO, null);
    Discover.Endpoint endpoint = Discover.Endpoint.newBuilder()
            .setNodeId(ByteString.copyFrom(Parameter.p2pConfig.getNodeID()))
            .setPort(Parameter.p2pConfig.getPort())
            .setAddress(ByteString.copyFrom(Parameter.p2pConfig.getIp().getBytes()))
            .build();
    this.helloMessage = Connect.HelloMessage.newBuilder()
            .setFrom(endpoint)
            .setVersion(Parameter.p2pConfig.getVersion())
            .setCode(code.getValue())
            .setTimestamp(System.currentTimeMillis()).build();
    this.type = MessageType.HANDSHAKE_HELLO;
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
            new String(from.getAddress().toByteArray()), from.getPort());
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
