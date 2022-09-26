package org.tron.p2p.connection.business.handshake;

import lombok.extern.slf4j.Slf4j;
import org.tron.p2p.base.Parameter;
import org.tron.p2p.connection.Channel;
import org.tron.p2p.connection.ChannelManager;
import org.tron.p2p.connection.message.handshake.HelloMessage;

@Slf4j(topic = "net")
public class HandshakeService {

  private int version = Parameter.p2pConfig.getVersion();

  public void sendHelloMsg(Channel channel, DisconnectCode code) {
    HelloMessage helloMessage = new HelloMessage(code);
    channel.send(helloMessage.getSendData());
    log.info("Handshake send to {}, {} ", channel.getInetAddress(), helloMessage);
  }

  public boolean handleHelloMsg(Channel channel, HelloMessage msg) {

    if (channel.isFinishHandshake()) {
      channel.close();
      log.warn("Close channel {}, handshake is finished", channel.getInetAddress());
      return false;
    }

    channel.setFinishHandshake(true);

    if (channel.isActive()) {
      if (msg.getCode() != DisconnectCode.NORMAL.getValue()
          || msg.getVersion() != version) {
        channel.close();
      }
      return false;
    }

    if (msg.getVersion() != version) {
      log.info("Peer {} different p2p version, peer->{}, me->{}",
          channel.getInetAddress(), msg.getVersion(), version);
      sendHelloMsg(channel, DisconnectCode.DIFFERENT_VERSION);
      channel.close();
      return false;
    }

    DisconnectCode code = ChannelManager.processPeer(channel);
    if (code != DisconnectCode.NORMAL) {
      sendHelloMsg(channel, code);
      channel.close();
      return false;
    }

    sendHelloMsg(channel, DisconnectCode.NORMAL);
    return true;
  }

}
