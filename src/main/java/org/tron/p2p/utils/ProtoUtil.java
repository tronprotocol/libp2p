package org.tron.p2p.utils;

import com.google.protobuf.ByteString;
import java.io.IOException;
import org.tron.p2p.protos.Connect;
import org.xerial.snappy.Snappy;

public class ProtoUtil {

  public static Connect.CompressMessage compressMessage(byte[] data) throws IOException {
    Connect.CompressMessage.CompressType type = Connect.CompressMessage.CompressType.uncompress;
    byte[] bytes = data;

    byte[] compressData = Snappy.compress(data);
    if (compressData.length < bytes.length) {
      type = Connect.CompressMessage.CompressType.compress_snappy;
      bytes = compressData;
    }

    return Connect.CompressMessage.newBuilder()
            .setData(ByteString.copyFrom(bytes))
            .setType(type).build();
  }

  public static byte[] uncompressMessage(Connect.CompressMessage message) throws IOException {
    if (message.getType().equals(Connect.CompressMessage.CompressType.uncompress)) {
      return message.getData().toByteArray();
    } else {
      return Snappy.uncompress(message.getData().toByteArray());
    }
  }

}
