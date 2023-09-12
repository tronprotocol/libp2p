package org.tron.p2p.connection.business.upgrade;

import java.io.IOException;
import org.tron.p2p.base.Parameter;
import org.tron.p2p.exception.P2pException;
import org.tron.p2p.protos.Connect;
import org.tron.p2p.utils.ProtoUtil;

public class UpgradeController {

  public static byte[] codeSendData(int version, byte[] data) throws IOException {
    if (!supportCompress(version)) {
      return data;
    }
    return ProtoUtil.compressMessage(data).toByteArray();
  }

  public static byte[] decodeReceiveData(int version, byte[] data)
      throws IOException, P2pException {
    if (!supportCompress(version)) {
      return data;
    }
    return ProtoUtil.uncompressMessage(Connect.CompressMessage.parseFrom(data));
  }

  private static boolean supportCompress(int version) {
    return Parameter.version >= 1 && version >= 1;
  }

}
