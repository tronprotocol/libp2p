package org.tron.p2p.connection.business.handshake;

import lombok.extern.slf4j.Slf4j;
import org.tron.p2p.base.Parameter;
import org.tron.p2p.connection.Channel;
import org.tron.p2p.connection.ChannelManager;
import org.tron.p2p.connection.business.MessageProcess;
import org.tron.p2p.connection.message.Message;
import org.tron.p2p.connection.message.handshake.HelloMessage;

@Slf4j(topic = "net")
public class HandshakeService implements MessageProcess {

  private final int networkId = Parameter.p2pConfig.getNetworkId();

  public void startHandshake(Channel channel) {
    sendHelloMsg(channel, DisconnectCode.NORMAL);
  }

  @Override
  public void processMessage(Channel channel, Message message) {
    HelloMessage msg = (HelloMessage) message;

    if (channel.isFinishHandshake()) {
      log.warn("Close channel {}, handshake is finished", channel.getInetAddress());
      channel.close();
      return;
    }

    channel.setFinishHandshake(true);
    channel.setNode(msg.getFrom());

    DisconnectCode code = ChannelManager.processPeer(channel);
    if (code != DisconnectCode.NORMAL) {
      sendHelloMsg(channel, code);
      channel.close();
      return;
    }

    ChannelManager.updateNodeId(channel, msg.getFrom().getHexId());
    if (channel.isDisconnect()) {
      return;
    }

    if (channel.isActive()) {
      if (msg.getCode() != DisconnectCode.NORMAL.getValue()
        || msg.getNetworkId() != networkId) {
        log.info("Handshake failed {}, code: {}, version: {}",
          channel.getInetAddress(),
          msg.getCode(),
          msg.getNetworkId());
        channel.close();
        return;
      }
    } else {
      if (msg.getNetworkId() != networkId) {
        log.info("Peer {} different p2p version, peer->{}, me->{}",
          channel.getInetAddress(), msg.getNetworkId(), networkId);
        sendHelloMsg(channel, DisconnectCode.DIFFERENT_VERSION);
        channel.close();
        return;
      }
      sendHelloMsg(channel, DisconnectCode.NORMAL);
    }

    channel.updateLatency(System.currentTimeMillis() - channel.getStartTime());
    Parameter.handlerList.forEach(h -> h.onConnect(channel));
  }

  private void sendHelloMsg(Channel channel, DisconnectCode code) {
    HelloMessage helloMessage = new HelloMessage(code);
    channel.send(helloMessage);
  }

}
