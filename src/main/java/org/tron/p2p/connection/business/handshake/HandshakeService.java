package org.tron.p2p.connection.business.handshake;

import lombok.extern.slf4j.Slf4j;
import org.tron.p2p.config.Parameter;
import org.tron.p2p.connection.Channel;
import org.tron.p2p.connection.ChannelManager;
import org.tron.p2p.connection.message.HelloMessage;

@Slf4j(topic = "net")
public class HandshakeService {

  private int version = Parameter.p2pConfig.getVersion();

  public void sendHelloMsg(Channel channel, DisconnectCode code) {
    HelloMessage helloMessage = new HelloMessage(code);
    channel.send(helloMessage.getData());
    log.info("Handshake send to {}, {} ", channel.getInetAddress(), helloMessage);
  }

  public void handleHelloMsg(Channel channel, HelloMessage msg) {

    if (channel.isFinishHandshake()) {
      channel.close();
      log.warn("Close channel {}, handshake is finished", channel.getInetAddress());
      return;
    }

    channel.setFinishHandshake(true);

    if (channel.isActive()) {
      if (msg.getCode() != DisconnectCode.NORMAL.getValue()
          || msg.getVersion() != version) {
        channel.close();
      }
      return;
    }

    if (msg.getVersion() != version) {
      log.info("Peer {} different p2p version, peer->{}, me->{}",
              channel.getInetAddress(), msg.getVersion(), version);
      sendHelloMsg(channel, DisconnectCode.DIFFERENT_VERSION);
      channel.close();
      return;
    }

    DisconnectCode code = ChannelManager.processPeer(channel);
    if (code != DisconnectCode.NORMAL) {
      sendHelloMsg(channel, code);
      channel.close();
      return;
    }

    sendHelloMsg(channel, DisconnectCode.NORMAL);

  }

}
