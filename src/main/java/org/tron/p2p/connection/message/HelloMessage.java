package org.tron.p2p.connection.message;

import com.google.protobuf.ByteString;
import org.apache.commons.lang3.ArrayUtils;
import org.bouncycastle.pqc.math.linearalgebra.ByteUtils;
import org.tron.p2p.config.Parameter;
import org.tron.p2p.connection.business.handshake.DisconnectCode;
import org.tron.p2p.discover.Node;
import org.tron.p2p.protos.Connect;
import org.tron.p2p.protos.Discover;

public class HelloMessage extends Message {

  private Connect.HelloMessage helloMessage;

  public HelloMessage(byte[] data) throws Exception {
    super(data);
    byte[] rawData = ArrayUtils.subarray(data, 1, data.length);
    this.helloMessage = Connect.HelloMessage.parseFrom(rawData);
  }

  public HelloMessage(DisconnectCode code) {
    super(null);
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
    this.type = Message.HELLO;
    byte[] bytes = new byte[1];
    bytes[0] = Message.HELLO;;
    this.data = ByteUtils.concatenate(bytes, helloMessage.toByteArray());
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
}
