package org.tron.p2p.discover.socket.message;

import com.google.protobuf.ByteString;
import java.util.ArrayList;
import java.util.List;
import org.tron.p2p.discover.Node;
import org.tron.p2p.protos.Discover;
import org.tron.p2p.protos.Discover.Endpoint;
import org.tron.p2p.protos.Discover.Neighbours;
import org.tron.p2p.protos.Discover.Neighbours.Builder;
import org.tron.p2p.utils.ByteArray;

import static org.tron.p2p.discover.socket.message.UdpMessageTypeEnum.DISCOVER_NEIGHBORS;

public class NeighborsMessage extends Message {

  private Discover.Neighbours neighbours;

  public NeighborsMessage(byte[] data) throws Exception {
    super(DISCOVER_NEIGHBORS, data);
    this.neighbours = Discover.Neighbours.parseFrom(data);
  }

  public NeighborsMessage(Node from, List<Node> neighbours, long sequence) {
    super(DISCOVER_NEIGHBORS, null);
    Builder builder = Neighbours.newBuilder()
        .setTimestamp(sequence);

    neighbours.forEach(neighbour -> {
      Endpoint endpoint = Endpoint.newBuilder()
          .setAddress(ByteString.copyFrom(ByteArray.fromString(neighbour.getHost())))
          .setPort(neighbour.getPort())
          .setNodeId(ByteString.copyFrom(neighbour.getId()))
          .build();

      builder.addNeighbours(endpoint);
    });

    Endpoint fromEndpoint = Endpoint.newBuilder()
        .setAddress(ByteString.copyFrom(ByteArray.fromString(from.getHost())))
        .setPort(from.getPort())
        .setNodeId(ByteString.copyFrom(from.getId()))
        .build();

    builder.setFrom(fromEndpoint);

    this.neighbours = builder.build();

    this.data = this.neighbours.toByteArray();
  }

  public List<Node> getNodes() {
    List<Node> nodes = new ArrayList<>();
    neighbours.getNeighboursList().forEach(neighbour -> nodes.add(
        new Node(neighbour.getNodeId().toByteArray(),
            ByteArray.toStr(neighbour.getAddress().toByteArray()),
            neighbour.getPort())));
    return nodes;
  }

  @Override
  public long getTimestamp() {
    return this.neighbours.getTimestamp();
  }

  @Override
  public Node getFrom() {
    return Message.getNode(neighbours.getFrom());
  }

  @Override
  public String toString() {
    return "[neighbours: " + neighbours;
  }
}
