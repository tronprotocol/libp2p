package org.tron.p2p.utils;

import com.google.protobuf.ByteString;
import java.io.IOException;

import org.tron.p2p.base.Parameter;
import org.tron.p2p.protos.Connect;
import org.xerial.snappy.Snappy;

public class ProtoUtil {

  public static Connect.CompressMessage compressMessage(byte[] data) throws IOException {
    Connect.CompressMessage.CompressType type = Connect.CompressMessage.CompressType.uncompress;
    byte[] bytes = data;

    byte[] compressData = Snappy.compress(data);
    if (compressData.length < bytes.length) {
      type = Connect.CompressMessage.CompressType.snappy;
      bytes = compressData;
    }

    return Connect.CompressMessage.newBuilder()
            .setData(ByteString.copyFrom(bytes))
            .setType(type).build();
  }

  public static byte[] uncompressMessage(Connect.CompressMessage message) throws IOException {
    byte[] data = message.getData().toByteArray();
    if (message.getType().equals(Connect.CompressMessage.CompressType.uncompress)) {
      return data;
    }

    int length = Snappy.uncompressedLength(data);
    if (length >= Parameter.MAX_MESSAGE_LENGTH) {
      throw new IOException("message is too big, len=" + length);
    }

    byte[] d2 = Snappy.uncompress(data);
    if (d2.length >= Parameter.MAX_MESSAGE_LENGTH) {
      throw new IOException("uncompressed message is too big, len=" + d2.length);
    }
    return d2;
  }

}
